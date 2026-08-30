import { spawn } from "node:child_process";
import path from "node:path";
import {
  exists,
  readInput,
  resetState,
  writeDocument,
} from "../common.mjs";

const operation = process.argv[2];
const jar = path.resolve(
  process.env.LDES_CT_EUROPEANA_ADAPTER_JAR ??
    "adapters/europeana/target/europeana-adapter.jar",
);

if (operation === "probe") {
  const available = await exists(jar);
  await writeDocument({
    protocolVersion: 0,
    available,
    adapterId: "europeana",
    clientVersion: "0.1-SNAPSHOT",
    clientRevision: process.env.LDES_CT_CLIENT_REVISION ?? "working-tree",
    capabilities: {
      modes: ["ordered"],
      memberExtraction: true,
      context: true,
      persistentState: true,
    },
    reason: available
      ? undefined
      : `adapter host JAR not found (expected ${path.relative(process.cwd(), jar)})`,
  });
} else if (operation === "reset") {
  await resetState(await readInput());
} else if (operation === "run") {
  await forwardJava(jar, await readInput());
} else {
  throw new Error(`unknown operation: ${operation}`);
}

async function forwardJava(jarPath, input) {
  await new Promise((resolve, reject) => {
    const child = spawn("java", ["-jar", jarPath], {
      stdio: ["pipe", "inherit", "inherit"],
    });
    child.on("error", reject);
    child.on("close", (code) =>
      code === 0 ? resolve() : reject(new Error(`Java host exited with ${code}`)),
    );
    child.stdin.end(`${JSON.stringify(input)}\n`);
  });
}

