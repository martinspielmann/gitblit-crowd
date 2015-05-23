/*
 *
 */
package org.obiba.git.gitblit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

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

			final List<String> groups = CrowdUtils.getUserGroups(client, um.getName());
			um.canAdmin = !Collections.disjoint(groups, crowdConfigUserService.adminGroups);

			um.teams.clear();
			um.teams.addAll(groups.stream().map(g-> {
				final TeamModel t = new TeamModel(g);
				return t;
			}).collect(Collectors.toSet()));
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

	private static List<String> getUserGroups(final CrowdClient client, final String user){
		try {
			final List<String> groups = client.getNamesOfGroupsForUser(user, 0, 1024);
				return groups;
		} catch (final UserNotFoundException e) {
			e.printStackTrace();
		} catch (final OperationFailedException e) {
			e.printStackTrace();
		} catch (final InvalidAuthenticationException e) {
			e.printStackTrace();
		} catch (final ApplicationPermissionException e) {
			e.printStackTrace();
		}
		return new ArrayList<>();
	}

	public static boolean userEqual(final CrowdClient client, final UserModel um, final String remoteUserName) {
		final User remoteUser = CrowdUtils.getUserByName(client, remoteUserName);
		final List<String> remoteUserGroups = CrowdUtils.getUserGroups(client, remoteUserName);


		return StringUtils.equals(um.displayName, remoteUser.getDisplayName())
					&& StringUtils.equals(um.emailAddress, remoteUser.getEmailAddress())
					&& teamsEqual(um.teams, remoteUserGroups);
	}

	private static boolean teamsEqual(final Set<TeamModel> teams, final List<String> remoteTeams){
		final List<String> collect = teams.stream().map(t->t.name).collect(Collectors.toList());
		Collections.sort(collect);
		Collections.sort(remoteTeams);
		return collect.equals(remoteTeams);
	}
}
