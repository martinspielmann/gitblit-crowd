/*
 *
 */
package org.obiba.git.gitblit;

import java.util.List;
import java.util.stream.Collectors;

import com.atlassian.crowd.exception.ApplicationPermissionException;
import com.atlassian.crowd.exception.InvalidAuthenticationException;
import com.atlassian.crowd.exception.OperationFailedException;
import com.atlassian.crowd.exception.UserNotFoundException;
import com.atlassian.crowd.model.user.User;
import com.atlassian.crowd.service.client.CrowdClient;
import com.gitblit.Constants;
import com.gitblit.Constants.AccountType;
import com.gitblit.models.TeamModel;
import com.gitblit.models.UserModel;

public class CrowdUtils {

	private CrowdUtils() {

	}

	public static UserModel mapCrowdUserToModel(final CrowdClient client,
			final User u, final CrowdConfigUserService crowdConfigUserService) {
		return mapCrowdUserToModel(client, new UserModel(u.getName()), crowdConfigUserService);
	}

	public static UserModel mapCrowdUserToModel(final CrowdClient client,
			final UserModel um, final CrowdConfigUserService crowdConfigUserService) {

		final User u = getUserByName(client, um.getName());

		um.accountType = AccountType.EXTERNAL;
		um.displayName = u.getDisplayName();
		um.password = Constants.EXTERNAL_ACCOUNT;
		um.emailAddress = u.getEmailAddress();

		try {
			final List<String> groups = client.getNamesOfGroupsForUser(
					u.getName(), 0, 1024);
			um.canAdmin = groups.contains("crowd-administrators");

			um.teams.clear();
			um.teams.addAll(groups.stream().map(g-> {
				final TeamModel t = new TeamModel(g);
				return t;
			}).collect(Collectors.toSet()));

//			for (final String g : groups) {
//				final TeamModel teamModel = new TeamModel(g);
//				teamModel.accountType = AccountType.EXTERNAL;
//				um.teams.add(teamModel);
//
//				for (final TeamModel repoTeam : CrowdConfigUserService.) {
//					if (teamModel.name.equals(repoTeam.name)) {
//						teamModel.permissions.putAll(repoTeam.permissions);
//						teamModel.canAdmin = repoTeam.canAdmin;
//						teamModel.canCreate = repoTeam.canCreate;
//						teamModel.canFork = repoTeam.canFork;
//					}
//				}
//			}
		} catch (final UserNotFoundException e) {
			e.printStackTrace();
		} catch (final OperationFailedException e) {
			e.printStackTrace();
		} catch (final InvalidAuthenticationException e) {
			e.printStackTrace();
		} catch (final ApplicationPermissionException e) {
			e.printStackTrace();
		}

		return um;
	}

	public static UserModel mapCrowdUserToModel(final CrowdClient client,
			final String username, final CrowdConfigUserService crowdConfigUserService) {
		return mapCrowdUserToModel(client, getUserByName(client, username), crowdConfigUserService);
	}

	private static User getUserByName(final CrowdClient client,
			final String username) {
		try {
			return client.getUser(username);
		} catch (final UserNotFoundException e) {
			e.printStackTrace();
		} catch (final OperationFailedException e) {
			e.printStackTrace();
		} catch (final ApplicationPermissionException e) {
			e.printStackTrace();
		} catch (final InvalidAuthenticationException e) {
			e.printStackTrace();
		}
		return null;
	}
}
