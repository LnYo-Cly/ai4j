/**
 * Copyright (c) 2025 Bytedance Ltd. and/or its affiliates
 * SPDX-License-Identifier: MIT
 */

import { createRoot } from 'react-dom/client';
import { unstableSetCreateRoot } from '@flowgram.ai/form-materials';

import { Editor } from './editor';

/**
 * React 18/19 polyfill for form-materials
 */
unstableSetCreateRoot(createRoot);

const app = createRoot(document.getElementById('root')!);

app.render(<Editor />);
