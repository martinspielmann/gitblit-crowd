/*
 *
 */
package org.obiba.git.gitblit;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
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



public class CrowdConfigUserService extends ConfigUserService{

	private static CrowdClient client;
    private static CrowdHttpAuthenticator authenticator;

    private final List<String> adminGroups = new ArrayList<>();
	private Integer syncInterval;
    private static final Logger log = LoggerFactory.getLogger(CrowdConfigUserService.class);

    public CrowdConfigUserService() {
		super(new File("users.conf"));
	}

    public CrowdConfigUserService(final File realmFile) {
		super(realmFile);
	}

    private Properties loadCrowdProperties(final File crowdProperties) {
        final Properties clientProperties = new Properties();
        try (FileInputStream is = new FileInputStream(crowdProperties)) {
            clientProperties.load(is);
            return clientProperties;
        } catch (final FileNotFoundException e) {
            log.error("crowd.properties file {} does not exist.", crowdProperties.getAbsolutePath());
            return null;
        } catch (final IOException e) {
            log.error("error reading crowd.properties file {}.", crowdProperties.getAbsolutePath());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setup(final IRuntimeManager manager) {
    	super.setup(manager);

        final File crowdFile = manager.getFileOrFolder(manager.getSettings().getString("crowd.properties", "crowd.properties"));
        final Properties crowdProperties = this.loadCrowdProperties(crowdFile);
        if (crowdProperties != null) {
            final ClientProperties crowdClientProperties = ClientPropertiesImpl.newInstanceFromProperties(crowdProperties);

            client = new RestCrowdClientFactory().newInstance(crowdClientProperties);
            // SSO Necessary stuff
            authenticator = new CrowdHttpAuthenticatorImpl(client, crowdClientProperties,
                    CrowdHttpTokenHelperImpl.getInstance(CrowdHttpValidationFactorExtractorImpl.getInstance()));

            final File permsFile = manager.getFileOrFolder(manager.getSettings().getString("crowd.permFile", "perms.xml"));
            log.info("crowd permissions file {}", permsFile.getAbsolutePath());
//            repo = new RepositoryPermissionsManager(permsFile);

            // Populate the list of groups with administrative privileges
            this.adminGroups.addAll(Arrays.asList(crowdProperties.getProperty("crowd.admingroups").split(",")));
            log.info("crowd groups with admin privileges {}", this.adminGroups);

            this.syncInterval = Integer.valueOf(crowdProperties.getProperty("crowd.syncinterval", "15"));



            final Thread t = new Thread(new CrowdSyncJob(this));
            t.start();
        }
    }

	public void syncUsers() {
		log.info("syncing users");
		//initialize teams
		this.getAllTeamNames().forEach(n -> {
			TeamModel existingTeamModel = this.getTeamModel(n);
			if(existingTeamModel==null){
				existingTeamModel = new TeamModel(n);
				existingTeamModel.accountType=AccountType.EXTERNAL;
			}
			existingTeamModel.canAdmin=this.adminGroups.contains(n);
			this.updateTeamModel(existingTeamModel);
		});

        //initialize users
		this.getAllUsernames().forEach(n -> {
			final UserModel existingUserModel = this.getUserModel(n);
			if(existingUserModel==null){
				this.updateUserModel(CrowdUtils.mapCrowdUserToModel(client, n, this));
			}else{
				this.updateUserModel(n, CrowdUtils.mapCrowdUserToModel(client, existingUserModel, this));
			}
		});

		try {
			Thread.sleep(this.syncInterval * 60 * 1000);
		} catch (final InterruptedException e) {
			e.printStackTrace();
		}
		this.syncUsers();
	}

	@Override
    public List<String> getAllUsernames() {
        try {
            return client.searchUserNames(new TermRestriction<>(new PropertyImpl<>("active", Boolean.class),
                    true), 0, 4096);
        } catch (OperationFailedException|InvalidAuthenticationException|ApplicationPermissionException e) {
            log.error("error while fetching all user names", e);
        }
        return new ArrayList<>();
    }

    @Override
    public List<String> getAllTeamNames() {
        try {
            return client.searchGroupNames(NullRestrictionImpl.INSTANCE, 0, 4096);
        } catch (OperationFailedException|InvalidAuthenticationException|ApplicationPermissionException e) {
            log.error("error while fetching all team names", e);
        }
        return new ArrayList<>();
    }

    public static CrowdClient getCrowdClient() {
        return client;
    }

    public static CrowdHttpAuthenticator getCrowdAuthenticator() {
        return authenticator;
    }
}
