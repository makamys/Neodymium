// From jimfs, adapted into a helper class
/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package makamys.neodymium.util.virtualjar;

import static com.google.common.base.Preconditions.checkArgument;

import java.net.URLStreamHandler;

public class URLStreamHandlerHelper {

    private static final String JAVA_PROTOCOL_HANDLER_PACKAGES = "java.protocol.handler.pkgs";

    /**
     * Generic method that would allow registration of any properly placed {@code Handler} class.
     */
    public static void register(Class<? extends URLStreamHandler> handlerClass) {
        checkArgument("Handler".equals(handlerClass.getSimpleName()));

        String pkg = handlerClass.getPackage().getName();
        int lastDot = pkg.lastIndexOf('.');
        checkArgument(lastDot > 0, "package for Handler (%s) must have a parent package", pkg);

        String parentPackage = pkg.substring(0, lastDot);

        String packages = System.getProperty(JAVA_PROTOCOL_HANDLER_PACKAGES);
        if (packages == null) {
            packages = parentPackage;
        } else {
            packages += "|" + parentPackage;
        }
        System.setProperty(JAVA_PROTOCOL_HANDLER_PACKAGES, packages);
    }

}
