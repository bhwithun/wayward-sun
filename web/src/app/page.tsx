import { Dashboard } from "@/components/Dashboard";
import packageJson from "../../package.json";

export const dynamic = "force-dynamic";

export default function Page() {
  return <Dashboard version={packageJson.version} />;
}
