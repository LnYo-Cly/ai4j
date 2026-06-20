/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

const flowgramWebConfig = require('@flowgram.ai/eslint-config/eslint.web.config.js');

module.exports = {
  root: true,
  ...flowgramWebConfig,
  env: {
    browser: true,
    es2021: true,
    node: true,
  },
  parser: '@typescript-eslint/parser',
  plugins: Array.from(
    new Set([
      ...(flowgramWebConfig.plugins || []),
      '@typescript-eslint',
      'import',
      'jsx-a11y',
      'prettier',
      'react',
    ])
  ),
  rules: {
    ...(flowgramWebConfig.rules || {}),
    'no-console': 'off',
    'react/prop-types': 'off',
  },
  settings: {
    ...(flowgramWebConfig.settings || {}),
    react: {
      version: 'detect', // 自动检测 React 版本
    },
  },
  overrides: flowgramWebConfig.overrides || [],
};
