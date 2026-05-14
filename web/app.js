const apiUrlInput = document.querySelector("#apiUrl");
const fileInput = document.querySelector("#fileInput");
const chooseFileButton = document.querySelector("#chooseFileButton");
const generateButton = document.querySelector("#generateButton");
const downloadButton = document.querySelector("#downloadButton");
const fileName = document.querySelector("#fileName");
const fileMeta = document.querySelector("#fileMeta");
const statusTitle = document.querySelector("#statusTitle");
const statusText = document.querySelector("#statusText");
const transcriptPreview = document.querySelector("#transcriptPreview");
const transcriptCount = document.querySelector("#transcriptCount");
const questionCount = document.querySelector("#questionCount");
const quizOutput = document.querySelector("#quizOutput");
const steps = {
  file: document.querySelector("#stepFile"),
  upload: document.querySelector("#stepUpload"),
  lambda: document.querySelector("#stepLambda"),
  quiz: document.querySelector("#stepQuiz"),
};

let selectedFileName = "";
let generatedQuiz = null;
const defaultApiUrl = "https://zw0l2t96ii.execute-api.us-east-1.amazonaws.com/prod/quiz";

apiUrlInput.value = localStorage.getItem("quizApiUrl") || defaultApiUrl;

chooseFileButton.addEventListener("click", () => fileInput.click());
fileInput.addEventListener("change", handleFileChange);
transcriptPreview.addEventListener("input", updateTranscriptState);
apiUrlInput.addEventListener("input", () => {
  localStorage.setItem("quizApiUrl", apiUrlInput.value.trim());
  updateGenerateButton();
});
generateButton.addEventListener("click", uploadTranscript);
downloadButton.addEventListener("click", downloadQuizJson);

function handleFileChange(event) {
  const file = event.target.files[0];
  generatedQuiz = null;
  downloadButton.disabled = true;
  renderQuiz(null);

  if (!file) {
    selectedFileName = "";
    fileName.textContent = "No file selected";
    fileMeta.textContent = "TXT transcripts only";
    transcriptPreview.value = "";
    setStatus("Ready", "Choose the transcript file from your Desktop or project folder.");
    setSteps("file");
    updateTranscriptState();
    return;
  }

  if (!file.name.toLowerCase().endsWith(".txt")) {
    setStatus("Wrong File Type", "Choose a .txt transcript file.");
    fileInput.value = "";
    updateGenerateButton();
    return;
  }

  selectedFileName = file.name;
  fileName.textContent = file.name;
  fileMeta.textContent = `${formatBytes(file.size)} text file`;

  const reader = new FileReader();
  reader.onload = () => {
    transcriptPreview.value = String(reader.result || "");
    setStatus("Transcript Loaded", "Upload it to S3 to start the Lambda quiz workflow.");
    setSteps("upload");
    updateTranscriptState();
  };
  reader.onerror = () => {
    setStatus("Read Failed", "Could not read the selected file.");
    updateGenerateButton();
  };
  reader.readAsText(file);
}

async function uploadTranscript() {
  const apiUrl = apiUrlInput.value.trim();
  const transcript = transcriptPreview.value.trim();

  if (!apiUrl || !transcript) {
    updateGenerateButton();
    return;
  }

  generatedQuiz = null;
  downloadButton.disabled = true;
  setBusy(true);
  setSteps("upload");
  setStatus("Uploading", "Writing the transcript to S3 through API Gateway and Lambda.");
  renderUploadProgress("Uploading transcript to S3...");

  try {
    const response = await fetch(apiUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        sourceName: selectedFileName || "uploaded-transcript.txt",
        transcript,
      }),
    });

    const payload = await readJsonResponse(response);
    if (!response.ok) {
      throw new Error(payload.error || `Lambda returned HTTP ${response.status}`);
    }

    setSteps("lambda");
    setStatus("Lambda Running", `Uploaded ${payload.transcriptKey}. Waiting for ${payload.expectedQuizKey}.`);
    renderUploadProgress(`Transcript saved to s3://${payload.bucket}/${payload.transcriptKey}`, payload.expectedQuizKey);
    await pollForQuiz(apiUrl, payload.expectedQuizKey);
  } catch (error) {
    setStatus("Action Needed", error.message);
    renderError(error.message);
  } finally {
    setBusy(false);
  }
}

async function pollForQuiz(apiUrl, quizKey) {
  const pollUrl = `${apiUrl}?key=${encodeURIComponent(quizKey)}`;
  const maxAttempts = 30;

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    setStatus("Waiting For Quiz", `Checking S3 for quiz JSON (${attempt}/${maxAttempts}).`);
    await wait(attempt === 1 ? 1200 : 3000);

    const response = await fetch(pollUrl, { method: "GET" });
    const payload = await readJsonResponse(response);

    if (response.status === 202) {
      continue;
    }
    if (!response.ok) {
      throw new Error(payload.error || `Quiz lookup returned HTTP ${response.status}`);
    }

    generatedQuiz = payload;
    renderQuiz(payload);
    downloadButton.disabled = false;
    setSteps("quiz");
    setStatus("Quiz Ready", "The generated quiz JSON was read from S3 and rendered below.");
    return;
  }

  throw new Error("Timed out waiting for the quiz JSON. Check the Lambda logs or Bedrock model access.");
}

function renderUploadProgress(message, quizKey = "") {
  quizOutput.className = "quiz-output";
  questionCount.textContent = "Processing";
  quizOutput.innerHTML = "";
  quizOutput.appendChild(detailBlock("Current step", message));
  if (quizKey) {
    quizOutput.appendChild(detailBlock("Expected quiz object", quizKey));
  }
}

function renderQuiz(payload) {
  quizOutput.innerHTML = "";

  if (!payload || !Array.isArray(payload.questions)) {
    quizOutput.className = "quiz-output empty";
    quizOutput.textContent = "Generated questions will appear here after Lambda writes the quiz JSON to S3.";
    questionCount.textContent = "0 questions";
    return;
  }

  quizOutput.className = "quiz-output";
  questionCount.textContent = `${payload.questions.length} questions`;

  payload.questions.forEach((question, index) => {
    const article = document.createElement("section");
    article.className = "question";

    const title = document.createElement("h3");
    title.textContent = `${index + 1}. ${question.question || "Untitled question"}`;
    article.appendChild(title);

    const options = document.createElement("ul");
    options.className = "options";

    Object.entries(question.options || {}).forEach(([key, value]) => {
      const item = document.createElement("li");
      if (key === question.correctAnswer) {
        item.classList.add("correct");
      }

      const optionKey = document.createElement("span");
      optionKey.className = "option-key";
      optionKey.textContent = key;

      const optionText = document.createElement("span");
      optionText.textContent = value;

      item.append(optionKey, optionText);
      options.appendChild(item);
    });

    article.appendChild(options);
    quizOutput.appendChild(article);
  });
}

function renderError(message) {
  quizOutput.className = "quiz-output";
  questionCount.textContent = "Error";
  quizOutput.innerHTML = "";
  quizOutput.appendChild(detailBlock("Error", message));
}

function downloadQuizJson() {
  if (!generatedQuiz) {
    return;
  }

  const blob = new Blob([JSON.stringify(generatedQuiz, null, 2)], {
    type: "application/json",
  });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `${stripExtension(selectedFileName || "lecture")}-quiz.json`;
  link.click();
  URL.revokeObjectURL(url);
}

async function readJsonResponse(response) {
  const responseText = await response.text();
  return responseText ? JSON.parse(responseText) : {};
}

function updateTranscriptState() {
  const count = transcriptPreview.value.length;
  transcriptCount.textContent = `${count.toLocaleString()} chars`;
  updateGenerateButton();
}

function updateGenerateButton() {
  generateButton.disabled = !apiUrlInput.value.trim() || !transcriptPreview.value.trim();
}

function setBusy(isBusy) {
  generateButton.disabled = isBusy || !apiUrlInput.value.trim() || !transcriptPreview.value.trim();
  generateButton.textContent = isBusy ? "Working..." : "Upload Transcript";
}

function setStatus(title, message) {
  statusTitle.textContent = title;
  statusText.textContent = message;
}

function setSteps(activeStep) {
  const order = ["file", "upload", "lambda", "quiz"];
  const activeIndex = order.indexOf(activeStep);

  order.forEach((step, index) => {
    steps[step].classList.toggle("active", index === activeIndex);
    steps[step].classList.toggle("done", activeIndex > index);
  });
}

function detailBlock(label, value) {
  const section = document.createElement("section");
  section.className = "question detail";

  const title = document.createElement("h3");
  title.textContent = label;

  const text = document.createElement("p");
  text.textContent = value || "";

  section.append(title, text);
  return section;
}

function formatBytes(bytes) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

function stripExtension(name) {
  return name.replace(/\.[^/.]+$/, "");
}

function wait(milliseconds) {
  return new Promise((resolve) => {
    window.setTimeout(resolve, milliseconds);
  });
}

setSteps("file");
updateTranscriptState();
