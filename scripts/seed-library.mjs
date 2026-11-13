import fs from "node:fs/promises";
import { execFileSync } from "node:child_process";
const files = ["teremok", "kolobok", "shapochka"];
const events = await Promise.all(
  files.map(async (name) =>
    JSON.parse(
      await fs.readFile(
        new URL(`../examples/text-work-added-${name}.json`, import.meta.url),
        "utf8",
      ),
    ),
  ),
);
execFileSync(
  "docker",
  [
    "compose",
    "exec",
    "-T",
    "kafka",
    "/opt/kafka/bin/kafka-console-producer.sh",
    "--bootstrap-server",
    "kafka:9092",
    "--topic",
    "text-work-added",
  ],
  {
    input: events.map((e) => JSON.stringify(e)).join("\n") + "\n",
    timeout: 30000,
    stdio: ["pipe", "pipe", "pipe"],
  },
);
console.log(
  "В Kafka отправлены три исходных произведения: Теремок, Колобок и Красная Шапочка.",
);
