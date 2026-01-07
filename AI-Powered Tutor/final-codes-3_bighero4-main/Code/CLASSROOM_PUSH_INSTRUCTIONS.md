# Instructions: Pushing Code to GitHub Classroom

Your code needs to be pushed to: `https://github.com/Embedded-Systems-Workshop/final-codes-3_bighero4.git`

The repository expects all code files to be in a **Code** folder.

## Quick Method (Recommended)

### Option 1: Using GitHub Web Interface (Easiest)

1. **First, push your code to a branch:**
   ```bash
   ./push_to_classroom.sh
   ```
   (You'll need to authenticate - see authentication methods below)

2. **Then organize in GitHub:**
   - Go to: https://github.com/Embedded-Systems-Workshop/final-codes-3_bighero4
   - Create a new folder called `Code` (if it doesn't exist)
   - Move all your files into the `Code` folder using GitHub's web interface
   - Commit the changes

### Option 2: Organize Locally First

1. **Reorganize files into Code folder:**
   ```bash
   ./organize_code_folder.sh
   ```

2. **Review and commit:**
   ```bash
   git status
   git add .
   git commit -m "Organize files into Code folder"
   ```

3. **Push to classroom:**
   ```bash
   git push classroom <branch-name>:main
   ```

## Authentication Methods

### Method 1: Personal Access Token (Recommended)

1. Go to: https://github.com/settings/tokens
2. Click "Generate new token" > "Generate new token (classic)"
3. Give it a name (e.g., "Classroom Push")
4. Select scope: **repo** (full control of private repositories)
5. Click "Generate token"
6. **Copy the token** (you won't see it again!)

7. Use it to push:
   ```bash
   git remote set-url classroom https://YOUR_TOKEN@github.com/Embedded-Systems-Workshop/final-codes-3_bighero4.git
   git push classroom ui:main
   ```

### Method 2: GitHub CLI

1. Install GitHub CLI:
   ```bash
   # Ubuntu/Debian
   sudo apt install gh
   
   # Or download from: https://cli.github.com/
   ```

2. Authenticate:
   ```bash
   gh auth login
   ```

3. Push:
   ```bash
   ./push_to_classroom.sh
   ```

### Method 3: SSH Keys

1. Generate SSH key (if you don't have one):
   ```bash
   ssh-keygen -t ed25519 -C "your_email@example.com"
   ```

2. Add to GitHub:
   - Copy your public key: `cat ~/.ssh/id_ed25519.pub`
   - Go to: https://github.com/settings/keys
   - Click "New SSH key" and paste

3. Update remote:
   ```bash
   git remote set-url classroom git@github.com:Embedded-Systems-Workshop/final-codes-3_bighero4.git
   git push classroom ui:main
   ```

## Current Setup

- **Current remote (origin):** https://github.com/Kausheya2006/AI_TUTOR2.git
- **Classroom remote (classroom):** https://github.com/Embedded-Systems-Workshop/final-codes-3_bighero4.git
- **Current branch:** ui

## Direct Push Command

Once authenticated, you can push directly:

```bash
# Push current branch to classroom main
git push classroom ui:main

# Or push to a specific branch
git push classroom ui:code
```

## Verify

After pushing, check:
https://github.com/Embedded-Systems-Workshop/final-codes-3_bighero4

Make sure all your code files are in the `Code` folder!

