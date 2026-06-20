# Ask User Question Prompt

Create one concise question for the application user.

Input:
- Current task
- Missing decision or missing value
- Safe default, if one exists

Output:
- `question`: the exact user-facing question
- `reason`: why this answer is needed
- `choices`: optional short choices
- `defaultChoice`: optional recommended default
- `blocking`: whether the agent should pause until answered
