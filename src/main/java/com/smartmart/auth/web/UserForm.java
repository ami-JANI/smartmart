package com.smartmart.auth.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.smartmart.auth.domain.RoleName;
import com.smartmart.auth.service.UserData;
import com.smartmart.auth.service.UserSummary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The user form, with the field rules from the SRS (FR-AUTH-6, FR-AUTH-7). */
@Getter
@Setter
@NoArgsConstructor
public class UserForm {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3 to 50 characters")
    private String username;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name can have at most 100 characters")
    private String fullName;

    // Empty is allowed here: on an edit it keeps the old password; the service requires one on create.
    @Pattern(regexp = "|.{8,}", message = "Password must be at least 8 characters")
    private String password = "";

    @NotEmpty(message = "Choose at least one role")
    private List<RoleName> roles = new ArrayList<>();

    static UserForm of(UserSummary user) {
        UserForm form = new UserForm();
        form.username = user.username();
        form.fullName = user.fullName();
        form.roles = user.roles().stream().map(RoleName::valueOf).collect(Collectors.toCollection(ArrayList::new));
        return form;
    }

    UserData toData() {
        return new UserData(username, fullName, password, Set.copyOf(roles));
    }
}
