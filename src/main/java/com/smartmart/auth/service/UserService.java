package com.smartmart.auth.service;

import java.util.List;

/**
 * User administration (FR-AUTH-6, FR-AUTH-7). Every method reads from the
 * primary, so the list shows a change the administrator has just made.
 */
public interface UserService {

    /** Every user, active or not, by username. */
    List<UserSummary> list();

    /** @throws UserNotFoundException if it does not exist */
    UserSummary get(Long id);

    /**
     * Adds an active user.
     *
     * @return the new user's id
     * @throws UserRuleException if the username is taken, the password is
     *                           shorter than 8 characters, or no role is chosen
     */
    Long create(UserData data);

    /**
     * Changes a user's name, roles and, if one is given, password. The new
     * roles apply from the user's next request.
     *
     * @throws UserNotFoundException if it does not exist
     * @throws UserRuleException     for the same reasons as {@link #create}, or if
     *                               it would leave no active administrator
     */
    void update(Long id, UserData data);

    /**
     * Stops a user from logging in. Their sales history is kept.
     *
     * @param currentUsername the administrator making the change
     * @throws UserNotFoundException if it does not exist
     * @throws UserRuleException     if administrators try to deactivate themselves
     */
    void deactivate(Long id, String currentUsername);
}
