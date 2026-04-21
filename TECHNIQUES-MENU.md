# Claude Code Techniques Menu — Hackathon 2026

**Format:** 6-12 hour hackathon, teams of 1-3, website build, task announced day-of
**Prerequisites:** Claude Code Pro license ($125/mo), Mac or Linux, mostly TypeScript

---

## How to Use This Document

1. **Pick one technique** from the Techniques section
2. **Optionally add accelerators** from the Accelerators section — they layer on top of any technique
3. **Follow the prep guide** during your prep week — install, read docs, do one practice run
4. **Use the cheat sheet** on hackathon day as a quick reference

If you don't know what to pick — **Superpowers** for a guided experience, **Vanilla+** if you want full control.

---

## Techniques

---

### Vanilla+ (Baseline)

**What:** Claude Code with no frameworks. A well-crafted `CLAUDE.md`, good prompting, manual discipline.

**The bet:** A skilled prompt engineer with clean context beats any framework overhead.

**Works for:** 1-3 engineers. Best if you already use Claude Code daily.

**Prep:** Read [Claude Code docs](https://docs.anthropic.com/en/docs/claude-code). Prepare a project scaffold (Vite/Next.js + TypeScript). Write a `CLAUDE.md` with your conventions.

#### Cheat Sheet

**On task announcement:**

1. Write a 10-line spec in `CLAUDE.md` describing the task
2. Add rules: framework, file structure, "run tests before committing"
3. Start coding — one feature at a time

**Key commands:**

- `/compact` — reclaim context when conversation gets long
- `/clear` — fresh start if context gets polluted
- `Shift+Tab` — accept Claude's plan and execute

**Prompting tips:**

- Be specific: "Create a React component for X that does Y" > "Make the UI"
- After each feature: "Commit this with a descriptive message"
- When stuck: "Read the error, explain what's wrong, fix it"

---

### Superpowers

**What:** [Superpowers](https://github.com/obra/superpowers) — a skill framework that adds structured brainstorming, TDD, implementation plans, and systematic debugging to Claude Code.

**The bet:** Structured creative-then-execute workflow prevents wasted cycles on wrong approaches.

**Works for:** 1-3 engineers. Good for mixed-experience teams.

**Prep:** Follow the [Superpowers installation guide](https://github.com/obra/superpowers). Do one full cycle on a practice project: brainstorm → plan → implement → debug.

#### Cheat Sheet

**On task announcement:**

1. `/brainstorm` — explore approaches (10-15 min, don't skip)
2. `/write-plan` — turn chosen approach into implementation plan
3. Execute plan — Superpowers will guide TDD automatically
4. `/systematic-debugging` when stuck (instead of guessing)

**Core loop:**

```
brainstorm → plan → (write test → implement → verify) × N → debug if stuck
```

**Key skills:** `brainstorming`, `writing-plans`, `test-driven-development`, `executing-plans`, `systematic-debugging`

---

### GSD (Get Shit Done)

**What:** [GSD](https://github.com/cline/gsd) — project lifecycle management with phases, milestones, and state tracking that survives context resets.

**The bet:** Phase decomposition prevents the "80% done, nothing works" trap. State tracking means you never lose progress when context resets.

**Works for:** 1 engineer (GSD is single-agent focused). Strongest when the task has many features to juggle.

**Prep:** Install GSD skills. Run `/gsd:new-project` on a throwaway project. Learn the core loop by building something small. Read the [GSD docs](https://github.com/cline/gsd).

#### Cheat Sheet

**On task announcement:**

1. `/gsd:new-project` — context gathering (15 min)
2. `/gsd:new-milestone` — define "working demo with N features"
3. `/gsd:plan-phase` — plan first feature/component
4. `/gsd:execute-phase` — build it with verification
5. Repeat 3-4 for each feature
6. `/gsd:progress` — check where you stand anytime

**When context resets:** `/gsd:resume-work` — picks up exactly where you left off.

**Quick task:** `/gsd:quick` — skip ceremony for small tasks.

---

### BMAD Method

**What:** [BMAD](https://github.com/bmad-code-org/BMAD-METHOD) — "Breakthrough Method for Agile AI-Driven Development." Role-based AI agents (analyst, architect, developer), documentation-first. Free and open source.

**The bet:** Front-loading 30-40 min of role-based analysis (requirements → architecture → spec) saves 2+ hours of rework during implementation.

**Works for:** 2-3 engineers (one can drive spec phase while another preps scaffolding). Heaviest framework — biggest potential payoff if the task is complex.

**Prep:** Install [BMAD skills for Claude Code](https://github.com/aj-geddes/claude-code-bmad-skills). Run through one full cycle: Business Analyst → Product Manager → Architect → Developer. Read [BMAD docs](https://docs.bmad-method.org/).

#### Cheat Sheet

**On task announcement:**

1. BMad Master orchestrates — just follow the prompts
2. Business Analyst: capture requirements (10 min)
3. Product Manager: prioritize MVP features (10 min)
4. System Architect: design components (15 min)
5. Developer: implement against the generated spec

**Key principle:** No code without a spec. The spec is the contract. If you skip spec, you lose the BMAD advantage.

**Warning:** ~30-40 min before first line of code. This is by design. Don't panic.

---

### SpecKit (Spec-Driven Development)

**What:** [SpecKit](https://github.com/github/spec-kit) by GitHub — write specs before code, specs become the contract the AI follows. Cross-agent compatible.

**The bet:** A clear spec eliminates hallucination and scope creep. You know exactly when you're done.

**Works for:** 1-2 engineers. Good for methodical thinkers who want a lighter-weight spec approach than BMAD.

**Prep:** Install SpecKit CLI. Set up `.specify` directory. Practice the four-step flow on a small project. Read the [SpecKit blog post](https://github.blog/ai-and-ml/generative-ai/spec-driven-development-with-ai-get-started-with-a-new-open-source-toolkit/) and [repo docs](https://github.com/github/spec-kit).

#### Cheat Sheet

**On task announcement:**

1. `/speckit.specify` — document requirements (15 min)
2. `/speckit.plan` — generates implementation plan
3. `/speckit.tasks` — breaks plan into executable chunks
4. `/speckit.implement` — executes tasks against the spec

**Core loop:** specify → plan → tasks → implement → (check against spec) × N

**Spec = acceptance criteria.** When spec says "done," you're done. Resist adding features not in spec.

---

### PlanMode + Rich Context

**What:** Claude Code's built-in Plan Mode (`/plan`) with extensively prepared context documents. No external tools needed.

**The bet:** Deep upfront context (architecture docs, conventions, patterns) makes the AI dramatically more effective because it never guesses.

**Works for:** 1-2 engineers. Best for architects who want to stay in control of every design decision.

**Prep:** Write comprehensive context documents: architecture template, component patterns, testing strategy. Put everything into `CLAUDE.md`. Practice using Plan Mode — read [Claude Code docs on Plan Mode](https://docs.anthropic.com/en/docs/claude-code).

#### Cheat Sheet

**On task announcement:**

1. Enter Plan Mode (`/plan` or `Shift+Tab` to toggle)
2. Paste task description → get detailed plan (15-20 min)
3. Review, adjust, approve the plan
4. Exit Plan Mode → execute with full context loaded
5. Re-enter Plan Mode at each major decision point

**Context doc template for `CLAUDE.md`:**

```markdown
## Architecture

- Framework: [Next.js/Vite+React/etc]
- State: [Zustand/Redux/etc]
- Styling: [Tailwind/CSS Modules/etc]

## Conventions

- File naming: kebab-case
- Components: functional, no classes
- Tests: colocated, \*.test.tsx

## Patterns

- [Describe your preferred patterns]
```

**Key principle:** Quality of context > quantity of prompts. Front-load everything into `CLAUDE.md`.

---

## Accelerators

Add-ons that layer on top of **any** technique. Use any combination.

---

### Harness Engineering

**What:** Tests, lints, type checks, and pre-commit hooks as guardrails that constrain the AI. Based on principles coined by Mitchell Hashimoto and systematized by OpenAI (Feb 2026).

**Why it helps:** The AI writes better code when it has fast feedback loops. Test fails → AI reads error → AI fixes → test passes. The tighter this loop, the fewer bugs ship.

**How to add it:**

1. Pre-configure your scaffold with: ESLint (strict), TypeScript strict mode, Vitest, pre-commit hooks
2. Add to `CLAUDE.md`: _"Before implementing any feature, write a failing test. Run `npm test` after every change. Never skip linting."_
3. Optional (aggressive): Configure a Claude Code `PostToolUse` hook that auto-runs tests after file edits

**Rule:** Test → Implement → Test passes → Commit. Never commit red.

---

### Voice Input (Superwhisper)

**What:** [Superwhisper](https://superwhisper.com/) — speak your prompts instead of typing. Local, fast, coding-mode aware.

**Why it helps:** Voice is 3-5x faster than typing for describing features, explaining bugs, and brainstorming. Natural language prompts tend to be more descriptive and produce better AI output than terse keyboard shorthand.

**How to add it:**

1. Install Superwhisper, configure for coding/dictation mode
2. Practice voice-prompting during prep week — describe features, dictate specs, explain bugs out loud
3. On hackathon day: speak all prompts, keep keyboard for code review and small edits

---

### Context7 MCP (Live Documentation)

**What:** [Context7](https://github.com/upstash/context7) — an MCP server that injects up-to-date, version-specific library documentation directly into Claude Code's context.

**Why it helps:** Claude's training data has a knowledge cutoff. If your team picks a framework they're not deeply familiar with — or if a library released a breaking change — Context7 ensures Claude uses the correct, current API instead of hallucinating deprecated methods.

**How to add it:**

1. Install: `claude mcp add context7 -- npx -y @upstash/context7-mcp@latest`
2. That's it — Context7 auto-activates when Claude needs library docs
3. Free tier: 1,000 requests/month, 60/hour — enough for a hackathon day

**Best for:** Teams using frameworks they haven't used before or that have changed recently.

---

### Custom Commands (Reusable Prompts)

**What:** Claude Code's built-in custom commands — reusable prompt templates stored as markdown files in `.claude/commands/`.

**Why it helps:** Instead of typing the same complex prompt every time ("add a new feature with tests, update the index, run lint"), you invoke `/project:add-feature` and it's done consistently every time. Reduces prompt fatigue and ensures consistent quality across the hackathon day.

**How to add it:**

1. During prep week, create `.claude/commands/` in your project
2. Write markdown files for your most common operations:
   - `add-feature.md` — "Add a new feature: create component, write tests, update routes, run lint"
   - `fix-bug.md` — "Read the error, trace the root cause, write a regression test, fix it, verify"
   - `checkpoint.md` — "Run all tests, commit if green, report status"
3. On hackathon day: invoke with `/project:add-feature`, `/project:fix-bug`, etc.

---

### Subagents (Parallel Execution)

**What:** Claude Code's built-in Agent tool — spawn parallel sub-agents that work on independent tasks simultaneously.

**Why it helps:** While one agent builds the header component, another builds the API route, and a third writes tests. Parallel execution can dramatically increase throughput when the task has independent parts.

**How to add it:**

1. No setup needed — it's built into Claude Code
2. When you have independent tasks, tell Claude: "Build component A and component B in parallel using subagents"
3. Claude spawns agents, each works in isolation, results merge back

**Best for:** Tasks with clearly independent components (e.g., "build 5 pages" where each page is self-contained). Less useful when everything depends on everything else.

**Caveat:** Subagents consume context window. Use for genuinely independent work, not as a default.

---

## Kickstart Recommendations

These are **prep-week actions** that give you a head start on hackathon day. The task is unknown, but the environment isn't — set up everything you can before the clock starts.

### 1. Pre-Write Your `CLAUDE.md`

Don't start from zero on hackathon day. Write your `CLAUDE.md` during prep week with:

- Your preferred tech stack (framework, state management, styling, testing)
- Coding conventions (naming, file structure, import order)
- Rules for Claude ("always write tests", "use TypeScript strict", "commit after each feature")
- Error handling patterns, component structure, API conventions

**Pro tip:** If you have a `CLAUDE.md` or coding guidelines from a previous project — **migrate them**. Strip the project-specific parts, keep the conventions. A battle-tested instruction set is worth more than one written from scratch.

### 2. Scaffold the Codebase

Prepare a ready-to-go project template so you start coding features, not configuring build tools:

- **Frontend:** Vite + React + TypeScript + Tailwind (or your preferred stack)
- **Full-stack:** Next.js with App Router, or Vite + Express/Fastify backend
- **Monorepo (2-3 person teams):** Consider a simple monorepo (`/frontend`, `/backend`, `/shared`) so team members can work in parallel without stepping on each other
- Pre-install common dependencies (routing, forms, icons, etc.)
- Pre-configure: ESLint, Prettier, Vitest, TypeScript strict mode
- Add a working "hello world" route — confirm the dev server starts and tests pass

**The goal:** `git clone → npm install → npm run dev` works in under 60 seconds on hackathon day.

### 3. Prepare Reusable Custom Commands

Create `.claude/commands/` with templates for your most common operations:

- `add-feature.md` — standard feature implementation flow
- `fix-bug.md` — read error → trace → test → fix → verify
- `checkpoint.md` — run tests, lint, commit if green

These save you from typing the same detailed prompt 20 times during a hackathon.

### 4. Test Your Full Setup End-to-End

During prep week, build something small (a todo app, a landing page) using your chosen technique + accelerators. This reveals:

- Missing dependencies or broken configs
- Friction in the workflow you didn't expect
- Whether your `CLAUDE.md` actually produces the code style you want

**Don't skip this.** Teams that discover setup issues on hackathon day lose 1-2 hours. Teams that discover them during prep week lose 30 minutes.

### 5. Agree on Team Workflow

For teams of 2-3, decide **before** hackathon day:

- **Who drives Claude?** One person prompts, others review? Or rotate?
- **How do you split work?** By feature? Frontend/backend? One builds, one tests?
- **Git workflow?** Feature branches and PRs, or everyone on main with frequent commits?
- **When do you sync?** Every 30 minutes? After each feature? Only when blocked?

---

## Evaluation Framework

### Primary: Velocity (60%)

- **Runnable:** Does it start and work? (binary gate — 0 if no)
- **Features delivered:** Count of working features vs. task requirements
- **Completeness:** % of requirements addressed

### Secondary: Code Quality (25%)

- Stability during demo (no crashes)
- TypeScript strict compliance (`tsc --noEmit`)
- Tests exist and pass
- Reasonable file organization

### Tertiary: Team Experience (15%)

- Would you use this technique again? (1-5)
- How much time felt wasted on process vs. building? (1-5)
- Did the technique help when you got stuck? (1-5)
- Confidence in the code you shipped? (1-5)

### Rubric

| Score | Runnable            | Features                  | Quality                  | Experience                 |
| ----- | ------------------- | ------------------------- | ------------------------ | -------------------------- |
| 5     | Flawless demo       | All requirements + extras | Clean, tested, typed     | "Changed how I work"       |
| 4     | Works, minor issues | All core requirements     | Mostly clean, some tests | "I'd use this again"       |
| 3     | Works after restart | Most requirements         | Reasonable structure     | "It was fine"              |
| 2     | Partially works     | Some requirements         | Messy but functional     | "More overhead than value" |
| 1     | Doesn't run         | Few requirements          | Spaghetti                | "Never again"              |
| 0     | Won't start         | —                         | —                        | —                          |

---

## Setup Checklist (All Teams)

Before hackathon day, regardless of technique:

- [ ] Claude Code Pro activated and working
- [ ] Project scaffold ready and dev server starts clean
- [ ] Git repo initialized with first commit
- [ ] `CLAUDE.md` written with tech stack, conventions, and rules
- [ ] Chosen technique installed and tested
- [ ] Accelerators configured (if using)
- [ ] Custom commands created (if using)
- [ ] One practice run completed — built something small with full setup
- [ ] Team roles and workflow agreed (for teams of 2-3)

---

## What We'll Learn

1. **Framework overhead vs. value** — Do structured workflows justify their cost in a 6-12 hour sprint?
2. **Spec-first vs. code-first** — Does writing specs before code save time or waste it?
3. **Accelerator impact** — Do harness engineering, voice, and live docs measurably improve outcomes regardless of technique?
4. **Planning depth** — Where's the break-even between upfront planning and just coding?
5. **Skill floor** — Which technique helps the least experienced developers the most?
