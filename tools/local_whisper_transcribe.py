import argparse
import json
from pathlib import Path

from faster_whisper import WhisperModel


def build_aws_transcribe_json(job_name, source_file, language, transcript, segments):
    items = []
    aws_segments = []

    for segment in segments:
        segment_text = segment.text.strip()
        aws_segments.append(
            {
                "start_time": f"{segment.start:.2f}",
                "end_time": f"{segment.end:.2f}",
                "alternatives": [{"transcript": segment_text}],
            }
        )

        if segment.words:
            for word in segment.words:
                content = word.word.strip()
                if not content:
                    continue
                items.append(
                    {
                        "start_time": f"{word.start:.2f}",
                        "end_time": f"{word.end:.2f}",
                        "alternatives": [
                            {
                                "confidence": f"{word.probability:.4f}",
                                "content": content,
                            }
                        ],
                        "type": "pronunciation",
                    }
                )

    return {
        "jobName": job_name,
        "accountId": "local-whisper",
        "status": "COMPLETED",
        "results": {
            "transcripts": [{"transcript": transcript}],
            "items": items,
            "segments": aws_segments,
            "language_code": language,
        },
        "source": str(source_file),
        "engine": "faster-whisper",
    }


def main():
    parser = argparse.ArgumentParser(
        description="Transcribe a local media file and emit AWS Transcribe-shaped JSON."
    )
    parser.add_argument("media_file", type=Path)
    parser.add_argument("--model", default="base.en")
    parser.add_argument("--language", default="en")
    parser.add_argument("--output-dir", type=Path, default=Path("transcripts"))
    parser.add_argument("--job-name", default=None)
    args = parser.parse_args()

    if not args.media_file.exists():
        raise FileNotFoundError(args.media_file)

    args.output_dir.mkdir(parents=True, exist_ok=True)
    job_name = args.job_name or args.media_file.stem

    print(f"Loading Whisper model: {args.model}")
    model = WhisperModel(args.model, device="cpu", compute_type="int8")

    print(f"Transcribing: {args.media_file}")
    segments_iter, info = model.transcribe(
        str(args.media_file),
        language=args.language,
        beam_size=5,
        vad_filter=True,
        word_timestamps=True,
    )

    segments = []
    transcript_parts = []
    for segment in segments_iter:
        print(f"[{segment.start:8.2f}s -> {segment.end:8.2f}s] {segment.text.strip()}")
        segments.append(segment)
        transcript_parts.append(segment.text.strip())

    transcript = " ".join(part for part in transcript_parts if part).strip()
    language = info.language or args.language

    txt_path = args.output_dir / f"{job_name}.txt"
    json_path = args.output_dir / f"{job_name}.json"

    txt_path.write_text(transcript + "\n", encoding="utf-8")
    aws_json = build_aws_transcribe_json(
        job_name=job_name,
        source_file=args.media_file,
        language=language,
        transcript=transcript,
        segments=segments,
    )
    json_path.write_text(json.dumps(aws_json, indent=2), encoding="utf-8")

    print(f"Wrote transcript text: {txt_path}")
    print(f"Wrote AWS Transcribe JSON: {json_path}")


if __name__ == "__main__":
    main()
