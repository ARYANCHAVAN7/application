# ==============================================================================
# Suraksha Emergency Response Android Project Makefile
# ==============================================================================

# Environment Configuration (Default paths for WSL / Linux build environment)
export JAVA_HOME ?= /usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME ?= /home/f33/android-sdk
export PATH := $(JAVA_HOME)/bin:$(ANDROID_HOME)/platform-tools:$(ANDROID_HOME)/cmdline-tools/latest/bin:$(PATH)

# Android App Constants
PACKAGE_NAME = com.example.application
LAUNCHER_ACTIVITY = $(PACKAGE_NAME)/.LoadingActivity
CHAT_ACTIVITY = $(PACKAGE_NAME)/.FirstAidChatActivity
APK_DEBUG = app/build/outputs/apk/debug/app-debug.apk
ROOT_APK = Suraksha-Latest.apk
GENYMOTION_IP = 192.168.239.101:5555

# Detect ADB binary
ADB := $(shell which adb 2>/dev/null || which adb.exe 2>/dev/null || echo $(ANDROID_HOME)/platform-tools/adb)

.PHONY: all help build assemble apk clean test install run run-chat devices connect logcat test-groq

# Default target
all: build

help:
	@echo "=================================================================="
	@echo " Suraksha Android Project Build & Management"
	@echo "=================================================================="
	@echo " make build             - Build debug APK (assembleDebug)"
	@echo " make clean             - Clean build artifacts"	
	@echo " make test              - Run unit tests (testDebugUnitTest)"
	@echo " make install           - Install debug APK to connected device/emulator"
	@echo " make run               - Launch main app on device"
	@echo " make run-chat          - Launch AI First-Aid Chatbot (Groq LPU) directly"
	@echo " make devices           - List connected ADB devices"
	@echo " make connect           - Connect ADB to Genymotion emulator ($(GENYMOTION_IP))"
	@echo " make logcat            - Stream device logs filtered for $(PACKAGE_NAME)"
	@echo " make test-groq         - Test Groq API with openai/gpt-oss-120b model"
	@echo "=================================================================="

build: assemble

assemble:
	@echo "🔨 Building Debug APK..."
	@chmod +x ./gradlew 2>/dev/null || true
	./gradlew assembleDebug
	@if [ -f "$(APK_DEBUG)" ]; then \
		cp -f "$(APK_DEBUG)" "$(ROOT_APK)"; \
		echo "✅ Build successful! APK copied to $(ROOT_APK)"; \
	fi

apk: assemble

clean:
	@echo "🧹 Cleaning project..."
	@chmod +x ./gradlew 2>/dev/null || true
	./gradlew clean

test:
	@echo "🧪 Running unit tests..."
	@chmod +x ./gradlew 2>/dev/null || true
	./gradlew testDebugUnitTest

devices:
	@echo "📱 Checking ADB devices..."
	$(ADB) devices

connect:
	@echo "🔌 Connecting to Genymotion emulator at $(GENYMOTION_IP)..."
	$(ADB) connect $(GENYMOTION_IP) || true
	$(ADB) devices

install:
	@echo "📦 Installing APK onto device..."
	@if [ -f "$(ROOT_APK)" ]; then \
		$(ADB) install -r "$(ROOT_APK)"; \
	elif [ -f "$(APK_DEBUG)" ]; then \
		$(ADB) install -r "$(APK_DEBUG)"; \
	else \
		echo "APK not found. Building first..."; \
		$(MAKE) assemble; \
		$(ADB) install -r "$(ROOT_APK)"; \
	fi
	@echo "✅ Installed successfully!"

run: install
	@echo "🚀 Launching Suraksha Main Activity..."
	$(ADB) shell am start -n $(LAUNCHER_ACTIVITY)

run-chat: install
	@echo "⚡ Launching Suraksha AI First-Aid Assistant (Groq openai/gpt-oss-120b)..."
	$(ADB) shell am start -n $(CHAT_ACTIVITY)

logcat:
	@echo "📋 Streaming logcat for $(PACKAGE_NAME)..."
	$(ADB) logcat -v time | grep -iE "$(PACKAGE_NAME)|GroqApiClient|Suraksha"

test-groq:
	@echo "🤖 Testing Groq API endpoint with openai/gpt-oss-120b..."
	@KEY=$$(grep -E '^GROQ_API_KEY=' local.properties | cut -d '=' -f2 | tr -d '\r\n'); \
	curl -s -X POST https://api.groq.com/openai/v1/chat/completions \
		-H "Authorization: Bearer $$KEY" \
		-H "Content-Type: application/json" \
		-d '{"model":"openai/gpt-oss-120b","messages":[{"role":"user","content":"Quick first aid for nosebleed in 2 bullet points."}],"max_tokens":120}' \
		| grep -o '"content":".*"' || true
