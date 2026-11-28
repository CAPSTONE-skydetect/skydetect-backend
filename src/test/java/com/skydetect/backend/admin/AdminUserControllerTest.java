package com.skydetect.backend.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skydetect.backend.auth.AuthProperties;
import com.skydetect.backend.auth.UserPrincipal;
import com.skydetect.backend.user.Role;
import com.skydetect.backend.user.User;
import com.skydetect.backend.user.UserRepository;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AdminUserControllerTest {
    private UserRepository users;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
    }

    private AdminUserController controller(AuthProperties.InitialOperator initial) {
        return new AdminUserController(users,
                new AuthProperties(Duration.ofHours(12), initial, "http://localhost", Duration.ofMinutes(30)));
    }

    private UserPrincipal principal(String username) {
        User user = User.builder().username(username).password("{bcrypt}hash").role(Role.ROLE_OPERATOR).build();
        return new UserPrincipal(user);
    }

    @Test
    void initialOperatorGetsUserListWithoutPassword() {
        User member = User.builder().username("member").email("m@example.com")
                .password("{bcrypt}secret").role(Role.ROLE_OPERATOR).build();
        when(users.findAll(any(Sort.class))).thenReturn(List.of(member));

        ResponseEntity<?> res = controller(new AuthProperties.InitialOperator("admin", "pw"))
                .listUsers(principal("admin"));

        assertEquals(HttpStatus.OK, res.getStatusCode());
        List<?> body = (List<?>) res.getBody();
        assertEquals(1, body.size());
        assertEquals("member", ((AdminUserController.AdminUserResponse) body.get(0)).username());
        assertFalse(body.get(0).toString().contains("secret"));
    }

    @Test
    void signedUpOperatorIsForbidden() {
        ResponseEntity<?> res = controller(new AuthProperties.InitialOperator("admin", "pw"))
                .listUsers(principal("member"));

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        verify(users, never()).findAll(any(Sort.class));
    }

    @Test
    void forbiddenWhenInitialOperatorNotConfigured() {
        assertEquals(HttpStatus.FORBIDDEN,
                controller(null).listUsers(principal("admin")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                controller(new AuthProperties.InitialOperator("", "")).listUsers(principal("")).getStatusCode());
    }
}
