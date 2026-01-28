package org.uwrf;

import software.amazon.awscdk.App;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        String studentName = (String) app.getNode().tryGetContext("studentName");

        if (studentName == null || studentName.isEmpty() || "CHANGE-ME".equals(studentName)) {
            throw new IllegalArgumentException(
                "Please set your studentName in cdk.json. " +
                "Edit the 'studentName' value from 'CHANGE-ME' to your IAM username."
            );
        }

        String stackName = studentName + "-UwrfStack";
        new UwrfStack(app, stackName, studentName);

        app.synth();
    }
}
