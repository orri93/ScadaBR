package com.serotonin.mango.util.freemarker;

import java.io.IOException;

import com.serotonin.mango.Common;
import com.serotonin.mango.db.dao.SystemSettingsDao;
import br.org.scadabr.web.email.TemplateEmailContent;

import freemarker.template.Template;
import freemarker.template.TemplateException;

public class MangoEmailContent extends TemplateEmailContent {

    public static final int CONTENT_TYPE_BOTH = 0;
    public static final int CONTENT_TYPE_HTML = 1;
    public static final int CONTENT_TYPE_TEXT = 2;

    public MangoEmailContent(String templateName, Object model, String encoding) throws TemplateException, IOException {
        this(templateName, model, encoding, SystemSettingsDao.getIntValue(SystemSettingsDao.EMAIL_CONTENT_TYPE));
    }

    private MangoEmailContent(String templateName, Object model, String encoding, int type) throws TemplateException,
            IOException {
        super(type == CONTENT_TYPE_HTML ? null : getTemplate(templateName, false), type == CONTENT_TYPE_TEXT ? null
                : getTemplate(templateName, true), model, encoding);
    }

    private static Template getTemplate(String name, boolean html) throws IOException {
        if (html) {
            name = "html/" + name + ".ftl";
        } else {
            name = "text/" + name + ".ftl";
        }

        return Common.ctx.getFreemarkerConfig().getTemplate(name);
    }
}
