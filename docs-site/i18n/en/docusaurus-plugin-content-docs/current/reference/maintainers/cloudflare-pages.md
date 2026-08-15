---
sidebar_position: 1
title: "Cloudflare Pages Deployment"
description: "Recommended deployment for the AI4J docs site: Docusaurus + Cloudflare Pages + a custom domain. Covers GitHub-integrated automatic builds, global CDN delivery, the free tier, custom domain configuration, and the continuous release flow."
tags: [how-to]
---

# Cloudflare Pages Deployment

Recommended stack: **Docusaurus + Cloudflare Pages + custom domain**.

## 1. Why Cloudflare Pages

- Free tier available for open-source projects
- Automatic builds and previews once integrated with GitHub
- Global CDN delivery — fast access to a static site

## 2. Pre-deployment Checklist

1. `url`/`baseUrl` in `docs-site/docusaurus.config.ts` are correct
2. Local `npm run build` succeeds
3. No broken links in the docs
4. Target branch strategy is clear (main/dev)

## 3. Cloudflare Pages Configuration

In the console, go to `Workers & Pages -> Create -> Pages`:

- Framework preset: `Docusaurus`
- Root directory: `docs-site`
- Build command: `npm run build`
- Build output directory: `build`
- Environment variable: `NODE_VERSION=20`

## 4. Verification After First Deployment

- Is the homepage reachable
- Is `/docs/intro` reachable
- Are key topic pages reachable, e.g. `/docs/coding-agent/overview`
- Does the 404 page render in the expected locale

## 5. Custom Domain

Recommended binding: `docs.ai4j.dev`

After binding, verify:

- DNS resolution has taken effect
- HTTPS certificate status is healthy
- canonical URL and sitemap are correct

## 6. Branch Strategy Recommendations

- `main`: production docs
- `dev`: pre-release docs
- PR branches: preview environment

## 7. Common Issues

### 7.1 404 After Deployment

Investigate in this order:

1. Is the Root directory mistakenly set to the repository root
2. Does `baseUrl` match the deployment path
3. Is Cloudflare still serving a cached older version

### 7.2 Pages Still Show Old Content

- Trigger a redeploy
- Run a Purge Cache

### 7.3 Local Renders Chinese, Production Renders English

- Check that `i18n.defaultLocale` is `zh-Hans`
- Check whether stale translation override files were left in place
- Rebuild and redeploy to avoid incremental cache pollution

## 8. Continuous Integration Recommendations

The repository can already wire up a docs build workflow (e.g. `.github/workflows/docs-build.yml`). Recommended to run automatically on every PR:

- markdown lint (optional)
- docusaurus build
- broken link check
