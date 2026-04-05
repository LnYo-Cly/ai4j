/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

export interface RuntimeBrowserOptions {
  mode?: 'browser';
}

export interface RuntimeServerOptions {
  mode: 'server';
  serverConfig: ServerConfig;
}

export type RuntimePluginOptions = RuntimeBrowserOptions | RuntimeServerOptions;

export interface ServerConfig {
  domain: string;
  port?: number;
  protocol?: string;
}
