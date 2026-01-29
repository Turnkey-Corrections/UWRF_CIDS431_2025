# UWRF CIDS 431 - AWS CDK Final Project

Welcome! In this project, you'll build a cloud-based system that automatically generates quizzes from lecture videos. By the end, you'll have hands-on experience with AWS services that are used in real-world software engineering jobs.

## What is AWS CDK?

Think of AWS CDK (Cloud Development Kit) as a way to create cloud resources using Java code instead of clicking around in the AWS website.

Normally, if you wanted to create an S3 bucket (cloud file storage), you'd log into the AWS Console and click through a bunch of forms. With CDK, you write Java code like `new Bucket(this, "MyBucket")` and CDK creates it for you. This approach is called **Infrastructure as Code** - your cloud setup is just code that you can version control, share, and rerun.

### Why Should You Care?

1. **It's how the industry works**: Companies don't click around in AWS to set up servers. They use tools like CDK, Terraform, or CloudFormation. Learning this now gives you a real advantage in job interviews.

2. **Your IDE helps you**: Since you're writing Java, IntelliJ gives you autocomplete and catches errors before you deploy. Way better than guessing at configuration files.

3. **It's repeatable**: Need to tear down and rebuild your project? Run one command. Need to deploy to a different AWS account? Same code works.

4. **Sensible defaults**: CDK handles a lot of security and configuration automatically. Creating a secure S3 bucket takes 2 lines instead of 50 lines of configuration.

### How CDK Works (The Big Picture)

```
Your Java Code  ──────▶  CDK Synth  ──────▶  CloudFormation  ──────▶  Real AWS Resources
   (CdkStack.java)      (cdk synth)         (auto-generated)        (S3, Lambda, etc.)
```

1. You write Java code describing what AWS resources you want
2. `cdk synth` converts your code into a CloudFormation template (AWS's native format)
3. `cdk deploy` sends that template to AWS, which creates the actual resources

Don't worry if CloudFormation sounds unfamiliar - CDK handles it for you. Just know it's the behind-the-scenes format AWS uses.

## What We're Building

You're creating an automated **Lecture Video Quiz Generator**. Here's the cool part: you drop a video file into cloud storage, walk away, and come back to a fully generated quiz. No manual work.

### The Pipeline (How Data Flows)

```
┌─────────────┐     ┌─────────────┐     ┌─────────────────┐     ┌─────────────┐     ┌─────────────┐
│   Upload    │     │   Lambda    │     │      AWS        │     │     AWS     │     │    Quiz     │
│  Video to   │────▶│   Trigger   │────▶│   Transcribe    │────▶│   Bedrock   │────▶│   JSON in   │
│     S3      │     │  (Java)     │     │  (Speech→Text)  │     │    (AI)     │     │     S3      │
└─────────────┘     └─────────────┘     └─────────────────┘     └─────────────┘     └─────────────┘
```

### What Happens Step-by-Step

1. **You upload a video** (like `SampleVideo.mp4`) to an S3 bucket using the AWS Console or CLI
2. **S3 triggers Lambda**: When a new file appears, S3 automatically notifies your Lambda function - think of it like a webhook or event listener
3. **Lambda calls Transcribe**: Your Java code asks AWS Transcribe to convert the audio to text. This takes a few minutes depending on video length.
4. **Lambda calls Bedrock**: Once you have the transcript, your code sends it to AWS Bedrock (Amazon's AI service, similar to ChatGPT) with a prompt like "Generate 10 multiple choice questions based on this lecture"
5. **Lambda saves the quiz**: Your code writes the AI-generated quiz as a JSON file back to S3

### AWS Services Explained

| Service | What It Is | Real-World Analogy |
|---------|------------|-------------------|
| **S3** | Cloud file storage | Like Google Drive or Dropbox, but for code to use |
| **Lambda** | Code that runs on-demand | A function that "wakes up" when triggered, then goes back to sleep. You only pay when it runs. |
| **Transcribe** | Speech-to-text service | Like YouTube's auto-captions, but as an API you can call |
| **Bedrock** | AI/Large Language Model service | Like ChatGPT, but hosted by AWS. You send it prompts and get responses. |
| **IAM** | Permissions system | Controls what each service is allowed to do (e.g., "Lambda can read from S3") |

### Project File Structure

```
UWRFCCSample/
├── src/
│   ├── main/java/org/uwrf/
│   │   ├── CdkApp.java           # Entry point - tells CDK which stacks to deploy
│   │   ├── UwrfStack.java        # Infrastructure definition (Lambda is set up, you add S3)
│   │   └── handlers/
│   │       └── VideoHandler.java # Lambda function - starter code provided, you complete it
│   └── test/java/org/uwrf/handlers/
│       └── VideoHandlerTest.java      # Tests for local development
├── iam/
│   └── student-policy.json   # IAM policy for student permissions (instructor use)
├── pom.xml                   # Maven dependencies (like package.json for Java)
├── cdk.json                  # CDK configuration (set your studentName here!)
└── SampleVideo.mp4           # Test video (download separately - see Getting Started)
```

### What's Provided vs. What You Build

We've given you starter code to get you going. Here's what's done and what's left for you:

| Component | Status | Your Task |
|-----------|--------|-----------|
| **Lambda Handler** | Starter code provided | Complete the TODO steps: call Transcribe, call Bedrock, write JSON to S3 |
| **CDK Stack (Lambda)** | Done | Lambda function is configured and ready to deploy |
| **CDK Stack (S3)** | TODO comments provided | Create the S3 bucket and connect it to Lambda |
| **CDK Stack (Permissions)** | TODO comments provided | Grant Lambda permission to use Transcribe, Bedrock, and S3 |
| **Local Testing** | Done | Test class and sample event provided |

### Example Quiz Output

After the pipeline runs, you'll find a JSON file in S3 that looks like this:

```json
{
  "sourceVideo": "SampleVideo.mp4",
  "generatedAt": "2025-01-28T10:30:00Z",
  "questions": [
    {
      "question": "What is the primary purpose of AWS Lambda?",
      "options": [
        "A) Store files in the cloud",
        "B) Run code without managing servers",
        "C) Send emails to users",
        "D) Create virtual machines"
      ],
      "correctAnswer": "B",
      "explanation": "Lambda is a serverless compute service that runs code in response to events."
    }
  ]
}
```

---

## Getting Started

### Prerequisites

Before you begin, make sure you have these installed:

| Tool | How to Check | How to Install |
|------|--------------|----------------|
| **Java 21** | `java -version` | Download from [Adoptium](https://adoptium.net/) or use IntelliJ's built-in JDK download |
| **Maven** | `mvn -version` | Usually comes with IntelliJ. If not: [Maven Install Guide](https://maven.apache.org/install.html) |
| **Node.js** | `node -version` | Download from [nodejs.org](https://nodejs.org/) (needed for CDK CLI) |
| **AWS CDK CLI** | `cdk --version` | Run `npm install -g aws-cdk` after installing Node.js |
| **AWS CLI** | `aws --version` | Download from [aws.amazon.com/cli](https://aws.amazon.com/cli/) |

### Download the Sample Video

The sample lecture video isn't included in the git repository (it's 805MB). Download it separately:

**Direct download link:** [SampleVideo.mp4](https://team-public.s3.us-west-2.amazonaws.com/SampleVideo.mp4)

Or use the command line:

```bash
# Windows PowerShell
Invoke-WebRequest -Uri "https://team-public.s3.us-west-2.amazonaws.com/SampleVideo.mp4" -OutFile "SampleVideo.mp4"

# Mac/Linux
curl -O https://team-public.s3.us-west-2.amazonaws.com/SampleVideo.mp4
```

Save the file in the root of your project folder (next to `pom.xml`). The file is ignored by git, so it won't be committed.

### Setting Up AWS Credentials

Your code needs permission to create resources in AWS. Here's how to set that up:

#### Step 1: Create Your AWS Account and Access Keys

Each student needs their own AWS account:

1. **Sign up for AWS** at [aws.amazon.com](https://aws.amazon.com/) using your own email and payment information
2. **Monitor your usage** to stay within the [AWS Free Tier](https://aws.amazon.com/free/) and avoid unexpected charges
3. **Create an Access Key** in the AWS Console:
   - Go to **IAM** → **Users** → Select your user (or create one)
   - Click **Security credentials** tab
   - Under **Access keys**, click **Create access key**
   - Choose **Command Line Interface (CLI)** as your use case
   - Download or copy both values:
     - **Access Key ID** (looks like `AKIAIOSFODNN7EXAMPLE`)
     - **Secret Access Key** (looks like random numbers and letters)

Keep these private! They're like a username and password for AWS.

#### Step 2: Configure the AWS CLI

Open your terminal and run:

```bash
aws configure
```

Enter the values when prompted:

```
AWS Access Key ID [None]: paste-your-access-key-here
AWS Secret Access Key [None]: paste-your-secret-key-here
Default region name [None]: us-east-1
Default output format [None]: json
```

#### Step 3: Verify It Worked

Run this command to test your credentials:

```bash
aws sts get-caller-identity
```

If successful, you'll see JSON with your account info:

```json
{
    "UserId": "AIDAEXAMPLEID",
    "Account": "123456789012",
    "Arn": "arn:aws:iam::123456789012:user/your-username"
}
```

If you get an error, double-check your Access Key and Secret Key for typos.

### Configure Your Student Name

Your resources are prefixed with your name to make them easily identifiable. **You must configure this before deploying.**

#### Step 1: Edit cdk.json

Open `cdk.json` and find the `studentName` field:

```json
"context": {
  "studentName": "CHANGE-ME",
  ...
}
```

Change `"CHANGE-ME"` to your name or a unique identifier (e.g., your last name or username):

```json
"context": {
  "studentName": "jsmith",
  ...
}
```

#### What This Does

When you deploy, all your AWS resources will be prefixed with your name:
- Stack: `jsmith-UwrfStack`
- Lambda function: `jsmith-video-handler`
- S3 bucket (when you add it): `jsmith-video-bucket`

This makes it easy to identify your resources in the AWS Console.

---

#### Where Are Credentials Stored?

The AWS CLI saves your credentials in a file on your computer:
- **Windows**: `C:\Users\YOUR_USERNAME\.aws\credentials`
- **Mac/Linux**: `~/.aws/credentials`

**Important**: Never commit this file to git or share it. If you accidentally expose your credentials, tell your instructor immediately so they can be deactivated.

#### Alternative: AWS IAM Identity Center (SSO)

If your instructor set up SSO (Single Sign-On), use these steps instead:

```bash
aws configure sso
```

Follow the prompts to log in via your browser. Then set the profile:

```bash
# Windows PowerShell
$env:AWS_PROFILE="your-profile-name"

# Windows Command Prompt
set AWS_PROFILE=your-profile-name

# Mac/Linux
export AWS_PROFILE=your-profile-name
```

---

## Development Workflow

**Important**: Don't deploy to AWS every time you make a change! That's slow and uses real cloud resources. Instead, test locally first, then deploy when you're confident your code works.

### The Recommended Workflow

```
1. Write code  →  2. Run tests locally  →  3. Fix issues  →  4. Repeat until tests pass  →  5. Deploy to AWS
```

### Testing Locally (Fast Feedback Loop)

We've set up a test class that lets you run your Lambda code on your own machine without deploying to AWS. This is way faster for debugging.

**Run all tests:**

```bash
mvn test
```

**Run just the VideoHandler tests:**

```bash
mvn test -Dtest=VideoHandlerTest
```

**What happens when you run tests:**

1. The test creates a fake S3 event programmatically (simulating what AWS sends)
2. It calls your `VideoHandler.handleRequest()` method with that fake event
3. Your code runs locally and prints output to the console
4. You see if it worked or if there were errors

**Example output when tests pass:**

```
=== Lambda Function Triggered ===
Received S3 event with 1 record(s)
--- S3 Event Details ---
Event Type: ObjectCreated:Put
Bucket: my-video-bucket
File: lectures/SampleVideo.mp4
Size: 844365824 bytes
------------------------
```

### Customizing the Test Event

To test different scenarios, edit the test in `VideoHandlerTest.java` or add new tests:

```java
@Test
void handleRequest_withMyCustomScenario() {
    // Change these values to simulate different uploads
    S3Event s3Event = createTestS3Event(
        "your-bucket-name",           // bucket name
        "path/to/your-video.mp4",     // file path in S3
        12345678L                      // file size in bytes
    );

    Context mockContext = new MockLambdaContext();
    String result = handler.handleRequest(s3Event, mockContext);

    // Add your assertions here
    assertNotNull(result);
}
```

### Why Local Testing Matters

| Approach | Time to See Results | Cost |
|----------|---------------------|------|
| Local testing (`mvn test`) | ~5 seconds | Free |
| Deploy to AWS (`cdk deploy`) | ~2-5 minutes | Uses AWS resources |

Local testing is 20-60x faster. Use it!

---

## Building and Deploying

Once your tests pass locally, you're ready to deploy to AWS.

### Compile Your Code

This checks that your Java code has no syntax errors:

```bash
mvn compile
```

If you see `BUILD SUCCESS`, you're good to go.

### Build the Lambda JAR

Before deploying, you need to package your Lambda code into a JAR file:

```bash
mvn package
```

This creates `target/lambda.jar` - a "fat JAR" containing your code and all its dependencies. CDK uploads this file to AWS when you deploy.

**Note**: If you skip this step, `cdk deploy` will fail because it can't find the JAR file.

### First-Time Setup: Bootstrap CDK

The first time you deploy to an AWS account, run this once:

```bash
cdk bootstrap
```

This creates some behind-the-scenes resources that CDK needs. You only do this once per AWS account/region.

### Preview Your Changes

Before deploying, see what CDK will create:

```bash
cdk synth
```

This outputs the CloudFormation template. You can also run:

```bash
cdk diff
```

This shows what will change compared to what's currently deployed.

### Deploy to AWS

When you're ready to create real resources, run these commands in order:

```bash
mvn package      # Build the Lambda JAR
cdk deploy       # Deploy to AWS
```

CDK will show you what it's about to create and ask for confirmation. Type `y` to proceed.

After deployment, you'll see outputs with your S3 bucket name and other useful info.

**Pro tip**: You can chain these commands so they run together:

```bash
mvn package && cdk deploy
```

### Tear Down (Clean Up)

When you're done with the project and want to delete all AWS resources:

```bash
cdk destroy
```

This removes everything CDK created, so you won't get charged for resources you're not using.

---

## Troubleshooting

### "Please set your studentName in cdk.json"

You haven't configured your student name yet. Edit `cdk.json` and change `"studentName": "CHANGE-ME"` to your IAM username. See [Configure Your Student Name](#configure-your-student-name).

### "Unable to locate credentials"

Your AWS credentials aren't configured. Run `aws configure` and enter your keys.

### "CDK not found" or "cdk is not recognized"

The CDK CLI isn't installed. Run `npm install -g aws-cdk` (requires Node.js).

### "Bootstrap required"

Run `cdk bootstrap` before your first deploy.

### "Access Denied" errors

This usually means your AWS credentials don't have the required permissions. Make sure your IAM user has permissions for Lambda, S3, Transcribe, Bedrock, and CloudFormation. If you're unsure, check with your instructor about the required IAM policies.

### Lambda times out

Lambda functions have a default timeout. If your video is long, the transcription might take too long. We'll configure an appropriate timeout in the stack.

### Changes not appearing after deploy

Make sure you saved your files and ran `mvn compile` before `cdk deploy`.

---

## Quick Reference

| Task | Command |
|------|---------|
| Check Java version | `java -version` |
| Check Maven version | `mvn -version` |
| Check CDK version | `cdk --version` |
| Compile code | `mvn compile` |
| Run tests locally | `mvn test` |
| Run specific test | `mvn test -Dtest=VideoHandlerTest` |
| Build Lambda JAR | `mvn package` |
| Preview deployment | `cdk diff` |
| Deploy to AWS | `mvn package && cdk deploy` |
| Delete resources | `cdk destroy` |
| Test AWS credentials | `aws sts get-caller-identity` |

---

## Need Help?

1. **Check the troubleshooting section above**
2. **Ask your instructor or TA**
3. **AWS Documentation**: [docs.aws.amazon.com](https://docs.aws.amazon.com/)
4. **CDK Java Reference**: [docs.aws.amazon.com/cdk/api/v2/java/](https://docs.aws.amazon.com/cdk/api/v2/java/)

Good luck with your final project!
