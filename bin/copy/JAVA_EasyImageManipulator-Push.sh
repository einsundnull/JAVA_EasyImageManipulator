#!/bin/bash
# whiteboard_git_deploy_advanced.sh
# === ERWEITERTE GIT DEPLOYMENT mit CONFLICT HANDLING ===

# === 1. Konfiguration ===
PROJEKT_PFAD="C:\Users\pc\eclipse-workspace\TransparencyTool"
GITHUB_URL="https://github.com/einsundnull/JAVA_EasyImageManipulator.git"

echo "🚀 Starte erweiterte Git Deployment für WhiteBoardApp..."

cd "$PROJEKT_PFAD" || {
  echo "❌ Pfad existiert nicht: $PROJEKT_PFAD"
  read -p "Drücke Enter zum Beenden..."
  exit 1
}

echo "✅ Arbeitsverzeichnis: $(pwd)"

# === Git Setup ===
if [ ! -d ".git" ]; then
  git init
  echo "✅ Git Repository initialisiert."
fi

git add -A
COMMIT_MSG="WhiteBoard Update - $(date '+%Y-%m-%d %H:%M:%S')"
git commit -m "$COMMIT_MSG" 2>/dev/null || echo "⚠️ Kein neuer Commit."

git branch -M main
git remote remove origin 2>/dev/null
git remote add origin "$GITHUB_URL"

# === ERWEITERTE PUSH OPTIONEN ===
echo "🚀 Versuche normalen Push..."
if git push origin main; then
  echo "✅ Push erfolgreich!"
  read -p "Drücke Enter zum Beenden..."
  exit 0
fi

echo "⚠️ Push fehlgeschlagen. Optionen:"
echo "1) FORCE PUSH (überschreibt GitHub Repository komplett)"
echo "2) FETCH & MERGE (versucht Remote-Änderungen zu integrieren)"  
echo "3) NEUES REPO erstellen (mit anderem Namen)"
echo "4) ABBRECHEN"

read -p "Wähle Option (1-4): " -n 1 -r
echo

case $REPLY in
  1)
    echo "🔥 FORCE PUSH - ÜBERSCHREIBT ALLES IM GITHUB REPO!"
    read -p "Wirklich alle GitHub-Inhalte löschen? (y/n): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
      git push --force origin main && echo "✅ Force Push erfolgreich!" || echo "❌ Force Push fehlgeschlagen!"
    fi
    ;;
  2)
    echo "🔄 Fetch & Merge..."
    git fetch origin
    git merge origin/main --allow-unrelated-histories -m "Merge remote changes"
    git push origin main && echo "✅ Merge Push erfolgreich!" || echo "❌ Merge Push fehlgeschlagen!"
    ;;
  3)
    NEW_REPO="JAVA_WhiteBoardApp_$(date '+%Y%m%d')"
    echo "🆕 Erstelle neues Repo: $NEW_REPO"
    echo "Gehe zu GitHub und erstelle: https://github.com/einsundnull/$NEW_REPO"
    read -p "Repo erstellt? Drücke Enter..."
    git remote set-url origin "https://github.com/einsundnull/$NEW_REPO.git"
    git push origin main && echo "✅ Neues Repo erfolgreich!" || echo "❌ Neues Repo fehlgeschlagen!"
    ;;
  4)
    echo "❌ Abgebrochen."
    ;;
esac

echo "🎉 Fertig!"
read -p "Drücke Enter zum Beenden..."