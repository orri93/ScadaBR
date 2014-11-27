/*
 Mango - Open Source M2M - http://mango.serotoninsoftware.com
 Copyright (C) 2006-2011 Serotonin Software Technologies Inc.
 @author Matthew Lohbihler
    
 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.serotonin.mango.rt.event.compound;

import br.org.scadabr.dao.CompoundEventDetectorDao;
import br.org.scadabr.rt.event.compound.CompoundEventManager;
import java.util.ArrayList;
import java.util.List;

import com.serotonin.mango.rt.event.EventDetectorListener;
import com.serotonin.mango.rt.event.SimpleEventDetector;
import com.serotonin.mango.rt.event.type.CompoundDetectorEventType;
import com.serotonin.mango.rt.event.type.SystemEventType;
import com.serotonin.mango.vo.event.CompoundEventDetectorVO;
import br.org.scadabr.util.ILifecycle;
import br.org.scadabr.util.LifecycleException;
import br.org.scadabr.utils.i18n.LocalizableException;
import br.org.scadabr.utils.i18n.LocalizableMessage;
import br.org.scadabr.utils.i18n.LocalizableMessageImpl;
import br.org.scadabr.vo.event.type.SystemEventKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;

/**
 * @author Matthew Lohbihler
 */
@Configurable
public class CompoundEventDetectorRT implements EventDetectorListener, ILifecycle {

    private static final char[] TOKEN_OR = {'|', '|'};
    private static final char[] TOKEN_AND = {'&', '&'};

    private final CompoundEventDetectorVO vo;
    @Autowired
    private CompoundEventManager compoundEventManager;
    @Autowired
    private CompoundEventDetectorDao compoundEventDetectorDao;
    
    private CompoundDetectorEventType eventType;
    private LogicalOperator condition;
    private boolean currentState;

    public CompoundEventDetectorRT(CompoundEventDetectorVO vo) {
        this.vo = vo;
    }

    private void raiseEvent(long time) {
        if (vo.isStateful()) {
            eventType.fire(time, "event.compound.activated", vo.getName());
        } else {
            eventType.fire(time, "event.compound.activated", vo.getName());
        }
    }

    private void returnToNormal(long time) {
        if (vo.isStateful()) {
            // no Alarm was raised . so no clearing ...
        } else {
            eventType.clearAlarm(time);
        }
    }

    public static LogicalOperator parseConditionStatement(String condition) throws ConditionParseException {
        if (condition.isEmpty()) {
            throw new ConditionParseException("compoundDetectors.validation.notDefined");
        }

        // Do bracket validation and character checking.
        int parenCount = 0;
        String allowedCharacters = "0123456789SP&|!()";
        for (int i = 0; i < condition.length(); i++) {
            char c = condition.charAt(i);
            if (allowedCharacters.indexOf(c) == -1 && !Character.isWhitespace(c)) {
                throw new ConditionParseException(i, i + 1, "compoundDetectors.validation.illegalChar");
            }

            if (c == '(') {
                parenCount++;
            } else if (c == ')') {
                parenCount--;
            }

            if (parenCount < 0) {
                throw new ConditionParseException(i, i + 1, "compoundDetectors.validation.closeParen");
            }
        }
        if (parenCount != 0) {
            int paren = condition.indexOf('(');
            throw new ConditionParseException(paren, paren + 1, "compoundDetectors.validation.openParen");
        }

        // Maintain the original indices of the characters
        List<ConditionStatementCharacter> charList = new ArrayList<>(condition.length());
        for (int i = 0; i < condition.length(); i++) {
            charList.add(new ConditionStatementCharacter(condition.charAt(i), i));
        }

        // Remove whitespace
        for (int i = condition.length() - 1; i >= 0; i--) {
            if (Character.isWhitespace(charList.get(i).c)) {
                charList.remove(i);
            }
        }

        // Convert to array.
        ConditionStatementCharacter[] chars = new ConditionStatementCharacter[charList.size()];
        charList.toArray(chars);

        // Parse into tokens
        return parseTokens(chars, 0, chars.length);
    }

    private static LogicalOperator parseTokens(ConditionStatementCharacter[] chars, int from, int to)
            throws ConditionParseException {
        if (from >= chars.length) {
            throw new ConditionParseException(chars[chars.length - 1].originalIndex, chars[chars.length - 1].originalIndex + 1, "compoundDetectors.validation.syntax");
        }

        if (to - from < 0) {
            throw new ConditionParseException(chars[from].originalIndex, chars[from].originalIndex + 1, "compoundDetectors.validation.syntax");
        }

        if (to - from == 0) {
            throw new ConditionParseException(chars[from].originalIndex, chars[to].originalIndex + 1, "compoundDetectors.validation.syntax");
        }

        // Find or conditions not in parens.
        int start = indexOfLevel0(TOKEN_OR, chars, from, to);
        if (start != -1) // Found an or condition.
        {
            return new OrOperator(parseTokens(chars, from, start), parseTokens(chars, start + TOKEN_OR.length, to));
        }

        // Find and conditions not in parens.
        start = indexOfLevel0(TOKEN_AND, chars, from, to);
        if (start != -1) // Found an and condition.
        {
            return new AndOperator(parseTokens(chars, from, start), parseTokens(chars, start + TOKEN_AND.length, to));
        }

        // Check for not
        if (chars[from].c == '!') {
            return new NotOperator(parseTokens(chars, from + 1, to));
        }

        // Check for parentheses.
        if (chars[from].c == '(' && chars[to - 1].c == ')') {
            return new Parenthesis(parseTokens(chars, from + 1, to - 1));
        }

        // Must be a detector reference. Check the syntax.
        if (to - from > 1 && Character.isLetter(chars[from].c)) {
            // The rest must be digits.
            for (int i = from + 1; i < to; i++) {
                if (!Character.isDigit(chars[i].c)) {
                    throw new ConditionParseException(chars[from].originalIndex, chars[to - 1].originalIndex + 1, "compoundDetectors.validation.reference");
                }
            }

            return new EventDetectorWrapper(toString(chars, from, to));
        }

        // Not good
        throw new ConditionParseException(chars[from].originalIndex, chars[to - 1].originalIndex + 1, "compoundDetectors.validation.reference");
    }

    private static int indexOfLevel0(char[] token, ConditionStatementCharacter[] chars, int from, int to) {
        int level = 0;
        boolean match;
        for (int i = from; i < to; i++) {
            if (chars[i].c == '(') {
                level++;
            } else if (chars[i].c == ')') {
                level--;
            } else if (level == 0) {
                // Try to match the token
                match = true;
                for (int j = 0; j < token.length && match; j++) {
                    if (token[j] != chars[i + j].c) {
                        match = false;
                    }
                }

                if (match) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String toString(ConditionStatementCharacter[] chars, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append(chars[i].c);
        }
        return sb.toString();
    }

    // public static void main(String[] args) {
    // //String condition = "  \t((S1 && P43) || (P1234 && !P5)\n\r || !S654)  \t";
    // //String condition = "P0 && (P1 && P2 && P3) || (P4 && P5) || !(P6 && !(P7 || P8))";
    // //String condition = "P8 && P15 || ";
    // String condition = "S2P8";
    //        
    // try {
    // System.out.println(parseConditionStatement(condition));
    // }
    // catch (ConditionParseException e) {
    // if (e.isRange())
    // System.out.println(e.getMessage() +" at "+ e.getFrom() +"-"+ e.getTo());
    // else
    // System.out.println(e.getMessage());
    // }
    // }
    public void raiseFailureEvent(LocalizableMessage message) {
        final SystemEventType failureEventType = new SystemEventType(SystemEventKey.COMPOUND_DETECTOR_FAILURE, vo.getId());
        failureEventType.fire(message);
        vo.setDisabled(true);
        compoundEventDetectorDao.saveCompoundEventDetector(vo);
    }

    //
    // / EventDetectorListener
    //
    @Override
    public void eventDetectorStateChanged(long time) {
        // Evaluate the condition.
        boolean newState = condition.evaluate();

        // If it has changed, take appropriate action.
        if (newState != currentState) {
            currentState = newState;
            if (currentState) // Active
            {
                raiseEvent(time);
            } else // Inactive
            {
                returnToNormal(time);
            }
        }
    }

    @Override
    public void eventDetectorTerminated(SimpleEventDetector source) {
        compoundEventManager.stopCompoundEventDetector(vo.getId());
        raiseFailureEvent(new LocalizableMessageImpl("event.compound.sourceFailure", vo.getName()));
    }

    //
    //
    // /
    // / Lifecycle interface
    // /
    //
    //
    @Override
    public void initialize() throws LifecycleException {
        // Validate the condition statement.
        try {
            condition = parseConditionStatement(vo.getCondition());
        } catch (ConditionParseException e) {
            throw new LifecycleException(e);
        }

        try {
            condition.initialize();
        } catch (LocalizableException e) {
            throw new LifecycleException(e);
        }

        condition.initSource(this);

        // Create a convenience reference to the event type.
        eventType = new CompoundDetectorEventType(vo);

        // Evaluate the current state.
        currentState = condition.evaluate();
        if (currentState) {
            raiseEvent(System.currentTimeMillis());
        } else {
            returnToNormal(System.currentTimeMillis());
        }
    }

    @Override
    public void terminate() {
        if (condition != null) {
            condition.terminate(this);
        }
        returnToNormal(System.currentTimeMillis());
    }

    @Override
    public void joinTermination() {
        // no op
    }

    static class ConditionStatementCharacter {

        char c;
        int originalIndex;

        public ConditionStatementCharacter(char c, int originalIndex) {
            this.c = c;
            this.originalIndex = originalIndex;
        }
    }
}
