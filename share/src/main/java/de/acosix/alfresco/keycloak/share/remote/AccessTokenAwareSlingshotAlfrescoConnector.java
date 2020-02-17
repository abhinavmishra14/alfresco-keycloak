/*
 * Copyright 2019 - 2020 Acosix GmbH
 *
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
package de.acosix.alfresco.keycloak.share.remote;

import java.util.Collections;

import javax.servlet.http.HttpSession;

import org.alfresco.web.site.servlet.SlingshotAlfrescoConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.extensions.config.RemoteConfigElement.ConnectorDescriptor;
import org.springframework.extensions.surf.ServletUtil;
import org.springframework.extensions.webscripts.connector.ConnectorContext;
import org.springframework.extensions.webscripts.connector.RemoteClient;

import de.acosix.alfresco.keycloak.share.deps.keycloak.KeycloakSecurityContext;
import de.acosix.alfresco.keycloak.share.deps.keycloak.adapters.OidcKeycloakAccount;
import de.acosix.alfresco.keycloak.share.deps.keycloak.adapters.spi.KeycloakAccount;
import de.acosix.alfresco.keycloak.share.util.RefreshableAccessTokenHolder;

/**
 * @author Axel Faust
 */
public class AccessTokenAwareSlingshotAlfrescoConnector extends SlingshotAlfrescoConnector
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AccessTokenAwareSlingshotAlfrescoConnector.class);

    /**
     * Constructs a new instance of this class.
     *
     * @param descriptor
     *            the descriptor / configuration of this connector
     * @param endpoint
     *            the endpoint with which this connector instance should connect
     */
    public AccessTokenAwareSlingshotAlfrescoConnector(final ConnectorDescriptor descriptor, final String endpoint)
    {
        super(descriptor, endpoint);
    }

    /**
     *
     * {@inheritDoc}
     */
    @Override
    protected void applyRequestAuthentication(final RemoteClient remoteClient, final ConnectorContext context)
    {
        final HttpSession session = ServletUtil.getSession();
        final KeycloakAccount keycloakAccount = (KeycloakAccount) (session != null ? session.getAttribute(KeycloakAccount.class.getName())
                : null);
        final RefreshableAccessTokenHolder accessToken = (RefreshableAccessTokenHolder) (session != null
                ? session.getAttribute(AccessTokenAwareSlingshotAlfrescoConnector.class.getName())
                : null);
        if (accessToken != null)
        {
            if (accessToken.isActive())
            {
                LOGGER.debug("Using access token for backend found in session for request");
                final String tokenString = accessToken.getToken();
                remoteClient.setRequestProperties(Collections.singletonMap("Authorization", "Bearer " + tokenString));
            }
            else
            {
                LOGGER.warn("Acesss token for backend stored in session has expired");
            }
        }
        else if (keycloakAccount instanceof OidcKeycloakAccount)
        {
            LOGGER.debug(
                    "Did not find access token for backend in session - using regularly authenticated Keycloak account access token for request instead");
            final KeycloakSecurityContext keycloakSecurityContext = ((OidcKeycloakAccount) keycloakAccount).getKeycloakSecurityContext();
            final String tokenString = keycloakSecurityContext.getTokenString();
            remoteClient.setRequestProperties(Collections.singletonMap("Authorization", "Bearer " + tokenString));
        }
        else
        {
            LOGGER.debug("Did not find Keycloak-related authentication data in session - applying regular request authentication");
            super.applyRequestAuthentication(remoteClient, context);
        }
    }
}