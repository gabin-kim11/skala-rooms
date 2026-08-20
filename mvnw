#!/bin/sh
set -eu

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION="3.9.16"
MAVEN_HOME="$BASE_DIR/.mvn/apache-maven-$MAVEN_VERSION"
ARCHIVE="$BASE_DIR/.mvn/apache-maven-$MAVEN_VERSION-bin.tar.gz"
DOWNLOAD_BASE="https://dlcdn.apache.org/maven/maven-3/$MAVEN_VERSION/binaries"

if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$BASE_DIR/.mvn"
  curl -fsSL "$DOWNLOAD_BASE/apache-maven-$MAVEN_VERSION-bin.tar.gz" -o "$ARCHIVE"
  EXPECTED=$(curl -fsSL "$DOWNLOAD_BASE/apache-maven-$MAVEN_VERSION-bin.tar.gz.sha512" | awk '{print $1}')
  ACTUAL=$(shasum -a 512 "$ARCHIVE" | awk '{print $1}')
  if [ "$EXPECTED" != "$ACTUAL" ]; then
    echo "Maven 다운로드 파일의 체크섬이 일치하지 않습니다." >&2
    exit 1
  fi
  tar -xzf "$ARCHIVE" -C "$BASE_DIR/.mvn"
  rm -f "$ARCHIVE"
fi

exec "$MAVEN_HOME/bin/mvn" -Dmaven.repo.local="$BASE_DIR/.mvn/repository" "$@"
