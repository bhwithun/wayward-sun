import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  eslint: { ignoreDuringBuilds: true },
  outputFileTracingRoot: process.cwd(),
  async redirects() {
    return [{ source: "/live", destination: "/", permanent: false }];
  },
};

export default nextConfig;
