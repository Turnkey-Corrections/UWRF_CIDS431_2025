package org.uwrf;

import software.amazon.awscdk.App;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        new UwrfStack(app, "UwrfStack");

        app.synth();
    }
}
