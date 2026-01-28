# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java 21 AWS CDK infrastructure project using Maven. The project serves as the foundation for AWS infrastructure provisioning.

## Build Commands

```bash
# Build the project
mvn compile

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=ClassName

# Run a single test method
mvn test -Dtest=ClassName#methodName

# Package the application
mvn package

# Synthesize CloudFormation template
cdk synth

# Deploy stack
cdk deploy

# Compare deployed stack with current state
cdk diff
```

## Architecture

This project follows the AWS CDK Java application structure:
- **CDK App** (`org.uwrf.CdkApp`): Entry point that defines which stacks to synthesize
- **Stacks**: Define AWS resources and their configurations
- **Constructs**: Reusable infrastructure components

## AWS CDK Notes

- CDK CLI must be installed globally (`npm install -g aws-cdk`)
- AWS credentials must be configured for deployment
- Use `cdk bootstrap` before first deployment to a new account/region
