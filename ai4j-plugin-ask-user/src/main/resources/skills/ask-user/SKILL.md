---
name: ask-user-collaboration
description: Ask the application user concise structured questions when missing information blocks a safe AI4J agent action.
---

# Ask User Collaboration

Use this skill when an AI4J agent needs a human decision, missing business context, or confirmation before continuing.

## Workflow

1. Ask only when the answer changes the next action.
2. Keep the question concrete and answerable.
3. Include short choices when they reduce ambiguity.
4. Explain why the answer is needed.
5. Resume only after the host application supplies the user's answer.

Do not ask broad preference questions when a reasonable default is already safe.
