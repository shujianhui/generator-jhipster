<%#
 Copyright 2013-2017 the original author or authors from the JHipster project.

 This file is part of the JHipster project, see http://www.jhipster.tech/
 for more information.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-%>
package <%=packageName%>.web.rest;

import <%=packageName%>.config.Constants;
import com.codahale.metrics.annotation.Timed;
<%_ if (authenticationType !== 'oauth2') { _%>
import <%=packageName%>.domain.User;<% } %>
import <%=packageName%>.repository.UserRepository;<% if (searchEngine === 'elasticsearch') { %>
import <%=packageName%>.repository.search.UserSearchRepository;<% } %>
import <%=packageName%>.security.AuthoritiesConstants;
<%_ if (authenticationType !== 'oauth2') { _%>
import <%=packageName%>.service.MailService;<% } %>
import <%=packageName%>.service.UserService;
import <%=packageName%>.service.dto.UserDTO;
<%_ if (authenticationType !== 'oauth2') { _%>
import <%=packageName%>.web.rest.errors.BadRequestAlertException;
import <%=packageName%>.web.rest.errors.EmailAlreadyUsedException;
import <%=packageName%>.web.rest.errors.LoginAlreadyUsedException;
import <%=packageName%>.web.rest.vm.ManagedUserVM;
import <%=packageName%>.web.rest.util.HeaderUtil;<% } %><% if (databaseType === 'sql' || databaseType === 'mongodb') { %>
import <%=packageName%>.web.rest.util.PaginationUtil;<% } %>
import io.github.jhipster.web.util.ResponseUtil;
import io.swagger.annotations.ApiParam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;<% if (databaseType === 'sql' || databaseType === 'mongodb') { %>
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;<% } %>
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

<%_ if (authenticationType !== 'oauth2') { _%>
import javax.validation.Valid;
import java.net.URI;
import java.net.URISyntaxException;
<%_ } _%>
import java.util.*;
<%_ if (searchEngine === 'elasticsearch') { _%>
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.*;
<%_ } _%>

/**
 * REST controller for managing users.
 * <p>
 * This class accesses the User entity, and needs to fetch its collection of authorities.
 * <p>
 * For a normal use-case, it would be better to have an eager relationship between User and Authority,
 * and send everything to the client side: there would be no View Model and DTO, a lot less code, and an outer-join
 * which would be good for performance.
 * <p>
 * We use a View Model and a DTO for 3 reasons:
 * <ul>
 * <li>We want to keep a lazy association between the user and the authorities, because people will
 * quite often do relationships with the user, and we don't want them to get the authorities all
 * the time for nothing (for performance reasons). This is the #1 goal: we should not impact our users'
 * application because of this use-case.</li>
 * <li> Not having an outer join causes n+1 requests to the database. This is not a real issue as
 * we have by default a second-level cache. This means on the first HTTP call we do the n+1 requests,
 * but then all authorities come from the cache, so in fact it's much better than doing an outer join
 * (which will get lots of data from the database, for each HTTP call).</li>
 * <li> As this manages users, for security reasons, we'd rather have a DTO layer.</li>
 * </ul>
 * <p>
 * Another option would be to have a specific JPA entity graph to handle this case.
 */
@RestController
@RequestMapping("/api")
public class UserResource {

    private final Logger log = LoggerFactory.getLogger(UserResource.class);

    private static final String ENTITY_NAME = "userManagement";

    private final UserRepository userRepository;
<%_ if (authenticationType !== 'oauth2') { _%>
    private final MailService mailService;
<%_ } _%>
    private final UserService userService;<% if (searchEngine === 'elasticsearch') { %>

    private final UserSearchRepository userSearchRepository;<% } %>

    public UserResource(UserRepository userRepository, <%_ if (authenticationType !== 'oauth2') { _%>MailService mailService,<%_ } _%>
            UserService userService<% if (searchEngine === 'elasticsearch') { %>, UserSearchRepository userSearchRepository<% } %>) {

        this.userRepository = userRepository;
        <%_ if (authenticationType !== 'oauth2') { _%>
        this.mailService = mailService;
        <%_ } _%>
        this.userService = userService;
        <%_ if (searchEngine === 'elasticsearch') { _%>
        this.userSearchRepository = userSearchRepository;
        <%_ } _%>
    }
<%_ if (authenticationType !== 'oauth2') { _%>

    /**
     * POST  /users  : Creates a new user.
     * <p>
     * Creates a new user if the login and email are not already used, and sends an
     * mail with an activation link.
     * The user needs to be activated on creation.
     *
     * @param managedUserVM the user to create
     * @return the ResponseEntity with status 201 (Created) and with body the new user, or with status 400 (Bad Request) if the login or email is already in use
     * @throws URISyntaxException if the Location URI syntax is incorrect
     * @throws BadRequestAlertException 400 (Bad Request) if the login or email is already in use
     */
    @PostMapping("/users")
    @Timed
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity createUser(@Valid @RequestBody ManagedUserVM managedUserVM) throws URISyntaxException {
        log.debug("REST request to save User : {}", managedUserVM);

        if (managedUserVM.getId() != null) {
            throw new BadRequestAlertException("A new user cannot already have an ID", ENTITY_NAME, "idexists");
        // Lowercase the user login before comparing with database
        } else if (userRepository.findOneByLogin(managedUserVM.getLogin().toLowerCase()).isPresent()) {
            throw new LoginAlreadyUsedException();
        } else if (userRepository.findOneByEmailIgnoreCase(managedUserVM.getEmail()).isPresent()) {
            throw new EmailAlreadyUsedException();
        } else {
            User newUser = userService.createUser(managedUserVM);
            mailService.sendCreationEmail(newUser);
            return ResponseEntity.created(new URI("/api/users/" + newUser.getLogin()))
                .headers(HeaderUtil.createAlert(<% if(enableTranslation) {%> "userManagement.created"<% } else { %> "A user is created with identifier " + newUser.getLogin()<% } %>, newUser.getLogin()))
                .body(newUser);
        }
    }

    /**
     * PUT  /users : Updates an existing User.
     *
     * @param managedUserVM the user to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated user
     * @throws EmailAlreadyUsedException 400 (Bad Request) if the email is already in use
     * @throws LoginAlreadyUsedException 400 (Bad Request) if the login is already in use
     */
    @PutMapping("/users")
    @Timed
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<UserDTO> updateUser(@Valid @RequestBody ManagedUserVM managedUserVM) {
        log.debug("REST request to update User : {}", managedUserVM);
        Optional<User> existingUser = userRepository.findOneByEmailIgnoreCase(managedUserVM.getEmail());
        if (existingUser.isPresent() && (!existingUser.get().getId().equals(managedUserVM.getId()))) {
            throw new EmailAlreadyUsedException();
        }
        existingUser = userRepository.findOneByLogin(managedUserVM.getLogin().toLowerCase());
        if (existingUser.isPresent() && (!existingUser.get().getId().equals(managedUserVM.getId()))) {
            throw new LoginAlreadyUsedException();
        }
        Optional<UserDTO> updatedUser = userService.updateUser(managedUserVM);

        return ResponseUtil.wrapOrNotFound(updatedUser,
            HeaderUtil.createAlert(<% if(enableTranslation) { %>"userManagement.updated"<% } else { %>"A user is updated with identifier " + managedUserVM.getLogin()<% } %>, managedUserVM.getLogin()));
    }
<% } %>
    /**
     * GET  /users : get all users.
     *<% if (databaseType === 'sql' || databaseType === 'mongodb') { %>
     * @param pageable the pagination information<% } %>
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/users")
    @Timed<% if (databaseType === 'sql' || databaseType === 'mongodb') { %>
    public ResponseEntity<List<UserDTO>> getAllUsers(@ApiParam Pageable pageable) {
        final Page<UserDTO> page = userService.getAllManagedUsers(pageable);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(page, "/api/users");
        return new ResponseEntity<>(page.getContent(), headers, HttpStatus.OK);
    }

    /**
     * @return a string list of the all of the roles
     */
    @GetMapping("/users/authorities")
    @Timed
    @Secured(AuthoritiesConstants.ADMIN)
    public List<String> getAuthorities() {
        return userService.getAuthorities();
    }<% } else { // Cassandra %>
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        final List<UserDTO> userDTOs = userService.getAllManagedUsers();
        return new ResponseEntity<>(userDTOs, HttpStatus.OK);
    }<% } %>

    /**
     * GET  /users/:login : get the "login" user.
     *
     * @param login the login of the user to find
     * @return the ResponseEntity with status 200 (OK) and with body the "login" user, or with status 404 (Not Found)
     */
    @GetMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @Timed
    public ResponseEntity<UserDTO> getUser(@PathVariable String login) {
        log.debug("REST request to get User : {}", login);
        return ResponseUtil.wrapOrNotFound(
            userService.getUserWithAuthoritiesByLogin(login)
                .map(UserDTO::new));
    }
<%_ if (authenticationType !== 'oauth2') { _%>

    /**
     * DELETE /users/:login : delete the "login" User.
     *
     * @param login the login of the user to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/users/{login:" + Constants.LOGIN_REGEX + "}")
    @Timed
    @Secured(AuthoritiesConstants.ADMIN)
    public ResponseEntity<Void> deleteUser(@PathVariable String login) {
        log.debug("REST request to delete User: {}", login);
        userService.deleteUser(login);
        return ResponseEntity.ok().headers(HeaderUtil.createAlert(<% if(enableTranslation) {%> "userManagement.deleted"<% } else { %> "A user is deleted with identifier " + login<% } %>, login)).build();
    }<%_ } _%><% if (searchEngine === 'elasticsearch') { %>

    /**
     * SEARCH  /_search/users/:query : search for the User corresponding
     * to the query.
     *
     * @param query the query to search
     * @return the result of the search
     */
    @GetMapping("/_search/users/{query}")
    @Timed
    public List<User> search(@PathVariable String query) {
        return StreamSupport
            .stream(userSearchRepository.search(queryStringQuery(query)).spliterator(), false)
            .collect(Collectors.toList());
    }<% } %>
}
