package com.siva.homeofveltech.UI;

import com.siva.homeofveltech.Network.AmsClient;

public class LoginHelper {

    public static boolean ensureLoggedIn(AmsClient ams, String username, String password) {
        try {
            // If cookies still valid, no need to login again
            if (ams.isSessionValid()) return true;

            // session expired -> login again
            return ams.login(username, password);

        } catch (Exception e) {
            return false;
        }
    }
}
