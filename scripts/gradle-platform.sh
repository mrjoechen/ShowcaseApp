#!/bin/sh
# A settings-level dependency profile affects the entire Gradle invocation.
set -eu
usage() {
    echo 'Usage: sh scripts/gradle-platform.sh standard|tvos TASK [TASK...] [Gradle options]' >&2
    exit 2
}
fail() { echo "error: $*" >&2; exit 2; }
[ "$#" -ge 2 ] || usage
profile=$1
shift
case "$profile" in
    standard) tvos=false ;;
    tvos) tvos=true ;;
    *) usage ;;
esac

has_task=false
option_value=false
for argument do
    case "$argument" in
        *showcase.tvos*) fail 'The entrypoint owns showcase.tvos; select standard or tvos instead.' ;;
    esac
    if [ "$option_value" = true ]; then option_value=false; continue; fi
    case "$argument" in
        -p|--project-dir|--project-dir=*|-p?*) fail 'This entrypoint always builds the Showcase repository.' ;;
        -P|-D|-x|--exclude-task|--project-prop|--system-prop|--max-workers|--console|--warning-mode|--tests|--configuration|--dependency|-g|--gradle-user-home) option_value=true; continue ;;
        -*) continue ;;
    esac
    has_task=true
    task=${argument##*:}
    lower_task=$(printf '%s' "$task" | tr '[:upper:]' '[:lower:]')
    if [ "$profile" = standard ]; then
        case "$lower_task" in *tvos*) fail "Use the tvos profile for $argument" ;; esac
    else
        case "$task" in
            *Tvos*|tvos*|clean|help|tasks|dependencies|dependencyInsight|properties|embedAndSignAppleFrameworkForXcode) ;;
            *) fail "The tvos profile requires explicit tvOS tasks; $argument may build other platforms." ;;
        esac
    fi
    if [ "$task" = embedAndSignAppleFrameworkForXcode ]; then
        case "$profile:${SDK_NAME:-}" in
            tvos:appletv*|standard:iphone*) ;;
            *) fail "The $profile profile does not match Xcode SDK_NAME=${SDK_NAME:-unset}." ;;
        esac
    fi
done
[ "$has_task" = true ] || usage
[ "$option_value" = false ] || usage
repository=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$repository"
exec ./gradlew "-Pshowcase.tvos=$tvos" "$@"
