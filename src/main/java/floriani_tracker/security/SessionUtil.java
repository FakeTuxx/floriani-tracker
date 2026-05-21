package floriani_tracker.security;

import floriani_tracker.model.AppUser;
import floriani_tracker.model.FireDepartment;
import floriani_tracker.model.Municipality;
import floriani_tracker.model.UserRole;
import jakarta.servlet.http.HttpSession;

public final class SessionUtil {
    public static final String SESSION_USER_KEY = "AUTHENTICATED_USER";

    private SessionUtil() {
    }

    public static SessionUser from(AppUser user) {
        SessionUser sessionUser = new SessionUser();
        sessionUser.setUserId(user.getId());
        sessionUser.setUsername(user.getUsername());
        sessionUser.setDisplayName(user.getDisplayName());
        sessionUser.setRole(user.getRole().name());
        sessionUser.setSuperAdmin(user.getRole() == UserRole.SUPER_ADMIN);

        FireDepartment department = user.getFireDepartment();
        if (department != null) {
            Municipality municipality = department.getMunicipality();
            sessionUser.setFireDepartmentId(department.getId());
            sessionUser.setFireDepartmentName(department.getName());
            sessionUser.setSubscriptionStatus(department.getSubscriptionStatus().name());
            sessionUser.setSubscriptionValidUntil(department.getSubscriptionValidUntil() == null ? null : department.getSubscriptionValidUntil().toString());

            if (municipality != null) {
                sessionUser.setMunicipalityId(municipality.getId());
                sessionUser.setMunicipalityName(municipality.getName());
                sessionUser.setMunicipalityDistrict(municipality.getDistrict());
                sessionUser.setMunicipalityState(municipality.getState());
                sessionUser.setLatitude(municipality.getLatitude());
                sessionUser.setLongitude(municipality.getLongitude());
            }
        }
        return sessionUser;
    }

    public static SessionUser requireUser(HttpSession session) {
        if (session == null) {
            throw new NotAuthenticatedException();
        }
        Object value = session.getAttribute(SESSION_USER_KEY);
        if (value instanceof SessionUser sessionUser) {
            return sessionUser;
        }
        throw new NotAuthenticatedException();
    }

    public static SessionUser requireSuperAdmin(HttpSession session) {
        SessionUser user = requireUser(session);
        if (!user.isSuperAdmin()) {
            throw new NotAuthenticatedException();
        }
        return user;
    }
}
