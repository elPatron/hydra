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
package com.addthis.hydra.job.web.jersey;

import com.sun.jersey.api.model.Parameter;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import com.yammer.dropwizard.auth.Auth;
import com.yammer.dropwizard.auth.Authenticator;
import com.yammer.dropwizard.auth.basic.BasicCredentials;

//import com.yammer.dropwizard.auth.basic.BasicAuthInjectable;

public class BasicAuthProvider<T> implements InjectableProvider<Auth, Parameter> {
    //static final Log LOG = Log.forClass(BasicAuthProvider.class);

    private final Authenticator<BasicCredentials, T> authenticator;
    private final String realm;

    /**
     * Creates a new {@link BasicAuthProvider} with the given {@link Authenticator} and realm.
     *
     * @param authenticator the authenticator which will take the {@link BasicCredentials} and
     *                      convert them into instances of {@code T}
     * @param realm         the name of the authentication realm
     */
    public BasicAuthProvider(Authenticator<BasicCredentials, T> authenticator, String realm) {
        this.authenticator = authenticator;
        this.realm = realm;
    }

    @Override
    public ComponentScope getScope() {
        return ComponentScope.PerRequest;
    }

    @Override
    public Injectable<?> getInjectable(ComponentContext ic,
            Auth a,
            Parameter c) {
        return new BasicAuthInjectable<>(authenticator, realm, a.required());
    }
}
