package org.dspace.app.xmlui.aspect.general;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.acting.AbstractConfigurableAction;
import org.apache.cocoon.environment.ObjectModelHelper;
import org.apache.cocoon.environment.Redirector;
import org.apache.cocoon.environment.Response;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.commons.lang.time.DateUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.dspace.app.xmlui.utils.HandleUtil;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;

import java.util.*;

/**
 * User: lantian @ atmire . com
 * Date: 11/13/13
 * Time: 5:30 PM
 */
public class LastModifiedDate extends AbstractConfigurableAction implements ThreadSafe {

        private FastDateFormat formatter = null;
        int days = 0;
        int hours = 0;
        int minutes = 0;
        int seconds = 0;

        public void configure(Configuration configuration)
                throws ConfigurationException {
            super.configure(configuration);

            // RFC-822 Date with a GMT based time zone
            this.formatter = FastDateFormat.getInstance("EEE, dd MMM yyyy kk:mm:ss zzz", DateUtils.UTC_TIME_ZONE);
            this.days = configuration.getChild("days").getValueAsInteger(0);
            this.hours = configuration.getChild("hours").getValueAsInteger(0);
            this.minutes = configuration.getChild("minutes").getValueAsInteger(0);
            this.seconds = configuration.getChild("seconds").getValueAsInteger(0);
        }

        public Map act(Redirector redirector, SourceResolver resolver,
                       Map objectModel, String source, Parameters parameters)
                throws Exception {
            Response response = ObjectModelHelper.getResponse(objectModel);
            Calendar calendar = Calendar.getInstance(DateUtils.UTC_TIME_ZONE);
            DSpaceObject dso = HandleUtil.obtainHandle(objectModel);
            Map values = new HashMap(3);

            /* Get the current time and output as the last modified header */
            String value = this.formatter.format(calendar);

            response.setHeader("Last-Modified", value);
            values.put("last-modified",  value);
            if(dso instanceof Item)
            {
                Item item = (Item) dso;
                Date lastModified = item.getLastModified();


                /* Advance the time as much as required */
                calendar.add(Calendar.DATE, lastModified.getDate());
                calendar.add(Calendar.HOUR, lastModified.getHours());
                calendar.add(Calendar.MINUTE, lastModified.getMinutes());
                calendar.add(Calendar.SECOND, lastModified.getSeconds());


            }
            else
            {
                /* Advance the time as much as required */
                calendar.add(Calendar.DATE, this.days);
                calendar.add(Calendar.HOUR, this.hours);
                calendar.add(Calendar.MINUTE, this.minutes);
                calendar.add(Calendar.SECOND, this.seconds);
            }
            /* Return the headers */
            return(Collections.unmodifiableMap(values));

        }
    }

