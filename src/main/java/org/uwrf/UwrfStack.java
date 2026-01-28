package org.uwrf;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class UwrfStack extends Stack {
    public UwrfStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public UwrfStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // Define your AWS resources here
    }
}
