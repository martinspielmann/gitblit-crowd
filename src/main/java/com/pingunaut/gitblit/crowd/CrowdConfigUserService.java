package com.pingunaut.gitblit.crowd;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.integration.http.CrowdHttpAuthenticator;
import com.atlassian.crowd.integration.http.CrowdHttpAuthenticatorImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpTokenHelperImpl;
import com.atlassian.crowd.integration.http.util.CrowdHttpValidationFactorExtractorImpl;
import com.atlassian.crowd.integration.rest.service.factory.RestCrowdClientFactory;
import com.atlassian.crowd.search.query.entity.restriction.NullRestrictionImpl;
import com.atlassian.crowd.search.query.entity.restriction.PropertyImpl;
import com.atlassian.crowd.search.query.entity.restriction.TermRestriction;
import com.atlassian.crowd.service.client.ClientProperties;
import com.atlassian.crowd.service.client.ClientPropertiesImpl;
import com.atlassian.crowd.service.client.CrowdClient;
import com.gitblit.ConfigUserService;
import com.gitblit.Constants.AccountType;
import com.gitblit.manager.IRuntimeManager;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

/**
 * The Class CrowdConfigUserService.
 */
public class CrowdConfigUserService extends ConfigUserService {

    private static final Logger LOG = LoggerFactory.getLogger(CrowdConfigUserService.class);

    private static CrowdClient client;
    private static CrowdHttpAuthenticator authenticator;

    public final List<String> adminGroups = new ArrayList<>();
    private Integer syncInterval;

    public CrowdConfigUserService(final IRuntimeManager runtimeManager) {
        super(runtimeManager.getFileOrFolder("${baseFolder}/users.conf"));
        LOG.info("CrowdConfigUserService created");
    }

    private Properties loadCrowdProperties(final Path crowdPropertiesFile) {
        final Properties clientProperties = new Properties();
        try (InputStream is = Files.newInputStream(crowdPropertiesFile)) {
            clientProperties.load(is);
            return clientProperties;
        } catch (final FileNotFoundException e) {
            LOG.error("crowd.properties file {} does not exist.", crowdPropertiesFile.toString());
            return null;
        } catch (final IOException e) {
            LOG.error("error reading crowd.properties file {}.", crowdPropertiesFile.toString());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setup(final IRuntimeManager manager) {
        super.setup(manager);

        Path crowdPropertiesFile = Paths.get(manager.getBaseFolder().getAbsolutePath(),
                manager.getSettings().getString("crowd.properties", "crowd.properties"));
        final Properties crowdProperties = this.loadCrowdProperties(crowdPropertiesFile);
        if (crowdProperties != null) {
            final ClientProperties crowdClientProperties = ClientPropertiesImpl.newInstanceFromProperties(crowdProperties);

            client = new RestCrowdClientFactory().newInstance(crowdClientProperties);
            // SSO stuff
            authenticator = new CrowdHttpAuthenticatorImpl(client, crowdClientProperties,
                    CrowdHttpTokenHelperImpl.getInstance(CrowdHttpValidationFactorExtractorImpl.getInstance()));

            // Populate the list of groups with administrative privileges
            this.adminGroups.addAll(Arrays.asList(crowdProperties.getProperty("crowd.admingroups").split(",")));
            LOG.info("crowd groups with admin privileges {}", this.adminGroups);

            this.syncInterval = Integer.valueOf(crowdProperties.getProperty("crowd.syncinterval", "60"));

            new Thread(new CrowdSyncJob(this)).start();
        }
    }

    public void syncUsers() {
        LOG.info("syncing teams");
        //initialize teams
        this.getAllTeamNames().forEach(n -> {
            TeamModel existingTeamModel = this.getTeamModel(n);
            if (existingTeamModel == null) {
                existingTeamModel = new TeamModel(n);
                existingTeamModel.accountType = AccountType.CONTAINER;
                this.updateTeamModel(existingTeamModel);
            } else if (existingTeamModel.canAdmin && !this.adminGroups.contains(n)) {
                existingTeamModel.canAdmin = false;
                this.updateTeamModel(existingTeamModel);
            } else if (!existingTeamModel.canAdmin && this.adminGroups.contains(n)) {
                existingTeamModel.canAdmin = true;
                this.updateTeamModel(existingTeamModel);
            }

        });
        LOG.info("syncing users");
        //initialize users
        this.getAllUsernames().forEach(n -> {
            final UserModel existingUserModel = this.getUserModel(n);
            if (existingUserModel == null) {
                this.updateUserModel(CrowdUtils.mapCrowdUserToModel(client, n, this));
            } else {
                if (!CrowdUtils.userEqual(client, existingUserModel, n)) {
                    this.updateUserModel(n, CrowdUtils.mapCrowdUserToModel(client, existingUserModel, this));
                }
            }
        });
        LOG.info("syncing finished");

        try {
            Thread.sleep(this.syncInterval * 60 * 1000);
        } catch (final InterruptedException e) {
            LOG.error("Error while running sync job", e);
        }
        this.syncUsers();
    }

    @Override
    public List<String> getAllUsernames() {
        try {
            return client.searchUserNames(new TermRestriction<>(new PropertyImpl<>("active", Boolean.class), true), 0, Integer.MAX_VALUE);
        } catch (OperationFailedException | InvalidAuthenticationException | ApplicationPermissionException e) {
            LOG.error("error while fetching all user names", e);
            return new ArrayList<>(0);
        }
    }

    @Override
    public List<String> getAllTeamNames() {
        try {
            return client.searchGroupNames(NullRestrictionImpl.INSTANCE, 0, Integer.MAX_VALUE);
        } catch (OperationFailedException | InvalidAuthenticationException | ApplicationPermissionException e) {
            LOG.error("error while fetching all team names", e);
            return new ArrayList<>(0);
        }
    }

    public static CrowdClient getCrowdClient() {
        return client;
    }

    public static CrowdHttpAuthenticator getCrowdAuthenticator() {
        return authenticator;
    }
}
