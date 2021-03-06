/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.util;

import java.io.OutputStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * simple util to send emails
 */
public class EmailUtil {

    private static Logger log = LoggerFactory.getLogger(EmailUtil.class);

    public static void email(String to, String subject, String body) {
        try {
            String[] cmd = {"mailx", "-s " + subject, to};
            Process p = Runtime.getRuntime().exec(cmd);
            OutputStreamWriter osw = new OutputStreamWriter(p.getOutputStream());
            osw.write(body);
            osw.close();
        } catch (Exception e) {
            log.warn("Unable to send email to : " + to + " due to : " + e.getMessage());
        }
    }
}
