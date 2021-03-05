package com.pingunaut.gitblit.crowd;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.wicket.protocol.http.WebRequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.crowd.exception.ApplicationAccessDeniedException;
import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.CrowdException;
import com.atlassian.crowd.exception.InvalidTokenException;
import com.atlassian.crowd.model.user.User;
import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.auth.AuthenticationProvider.UsernamePasswordAuthenticationProvider;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * @author Martin Spielmann
 */
public class CrowdAuthenticationProvider extends UsernamePasswordAuthenticationProvider {

    public CrowdAuthenticationProvider() {
        super(CrowdAuthenticationProvider.class.getName());
    }

    private static final Logger LOG = LoggerFactory.getLogger(CrowdAuthenticationProvider.class);

    @Override
    public void setup() {

    }

    @Override
    public AccountType getAccountType() {
        return AccountType.CONTAINER;
    }

    @Override
    public boolean supportsCredentialChanges() {
        return false;
    }

    @Override
    public boolean supportsDisplayNameChanges() {
        return false;
    }

    @Override
    public boolean supportsEmailAddressChanges() {
        return false;
    }

    @Override
    public boolean supportsTeamMembershipChanges() {
        return false;
    }

    @Override
    public boolean supportsRoleChanges(final UserModel userModel, final Constants.Role role) {
        return false;
    }

    @Override
    public boolean supportsRoleChanges(final TeamModel teamModel, final Constants.Role role) {
        return false;
    }

    @Override
    public void stop() {

    }

    private User doCrowdauthenticate(final String username, final String passwd) throws CrowdException, ApplicationPermissionException {
        final WebRequestCycle requestCycle = (WebRequestCycle) WebRequestCycle.get();
        if (requestCycle != null) {
            // Try an SSO authentication
            final HttpServletRequest request = requestCycle.getWebRequest().getHttpServletRequest();
            final HttpServletResponse response = requestCycle.getWebResponse().getHttpServletResponse();
            try {
                return CrowdConfigUserService.getCrowdAuthenticator().authenticate(request, response, username, passwd);
            } catch (ApplicationAccessDeniedException | InvalidTokenException e) {
                // ignore. if SSO fails, just to on with normal authentication
            }
        }
        return CrowdConfigUserService.getCrowdClient().authenticateUser(username, passwd);
    }

    @Override
    public UserModel authenticate(final String username, final char[] password) {
        try {
            final User crowdUser = this.doCrowdauthenticate(username, new String(password));
            return this.userManager.getUserModel(crowdUser.getName());
        } catch (final CrowdException e) {
            LOG.info("unable to authenticate user {}: {}", username, e.getMessage());
        } catch (final ApplicationPermissionException e) {
            LOG.warn("unable to authenticate application to crowd: {}", e.getMessage());
        }
        return null;
    }


}
