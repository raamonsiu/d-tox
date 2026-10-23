# Short, shared commands for building, running and debugging D-TOX.
# Run `make` or `make help` to list the targets. Choose a device with DEVICE=<serial>
# (see `make devices`); without it adb and Gradle use the only connected device.

APPLICATION_ID := dev.d1ito.dtox
LAUNCH_ACTIVITY := $(APPLICATION_ID)/.MainActivity
GRADLE := ./gradlew

# SDK lookup order: ANDROID_HOME, ANDROID_SDK_ROOT, sdk.dir in local.properties, then adb on PATH.
LOCAL_SDK_DIRECTORY := $(shell sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null)
ANDROID_SDK_DIRECTORY := $(or $(ANDROID_HOME),$(ANDROID_SDK_ROOT),$(LOCAL_SDK_DIRECTORY))
ADB := $(if $(ANDROID_SDK_DIRECTORY),$(ANDROID_SDK_DIRECTORY)/platform-tools/adb,adb)

ifdef DEVICE
export ANDROID_SERIAL := $(DEVICE)
endif

.DEFAULT_GOAL := help
.PHONY: help setup build test lint format check clean devices install run debug logcat

help: ## List the available targets
	@grep -E '^[a-z]+:.*## ' $(MAKEFILE_LIST) | awk -F ':.*## ' '{printf "  make %-8s %s\n", $$1, $$2}'

setup: ## Enable the git hooks and merge drivers (once per clone)
	scripts/setup.sh

build: ## Compile the debug APK
	$(GRADLE) assembleDebug

test: ## Run the unit tests
	$(GRADLE) testDebugUnitTest

lint: ## Run Android lint, ktlint and detekt
	$(GRADLE) lintDebug ktlintCheck detekt

format: ## Auto-format Kotlin sources with ktlint
	$(GRADLE) ktlintFormat

check: test lint ## Run tests and linters (what CI checks)

clean: ## Delete build outputs
	$(GRADLE) clean

devices: ## List connected devices and emulators
	$(ADB) devices -l

install: ## Build and install the debug APK on the device
	$(GRADLE) installDebug

run: install ## Install and open the app
	$(ADB) shell am start -n $(LAUNCH_ACTIVITY)

debug: install ## Install, open waiting for a debugger, and follow the app's logcat
	$(ADB) shell am start -D -n $(LAUNCH_ACTIVITY)
	@echo "Waiting for a debugger: in Android Studio use Run > Attach Debugger to Android Process."
	@$(MAKE) --no-print-directory logcat

logcat: ## Follow the logcat of the running app (Ctrl+C to stop)
	@process_identifier=$$($(ADB) shell pidof -s $(APPLICATION_ID) | tr -d '\r'); \
	if [ -z "$$process_identifier" ]; then echo "$(APPLICATION_ID) is not running." >&2; exit 1; fi; \
	$(ADB) logcat --pid=$$process_identifier
