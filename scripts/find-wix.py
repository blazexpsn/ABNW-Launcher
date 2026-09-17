#!/usr/bin/env python3
"""Find a WiX Toolset bin directory for jpackage on Windows."""

import os
import string
import sys
import argparse

try:
    import winreg
except ImportError:
    winreg = None


def wix_directory(directory):
    if not directory:
        return None
    directory = os.path.abspath(os.path.expandvars(os.path.expanduser(directory)))
    if os.path.isfile(os.path.join(directory, "wix.exe")):
        return directory
    if (os.path.isfile(os.path.join(directory, "candle.exe")) and
            os.path.isfile(os.path.join(directory, "light.exe"))):
        return directory
    return None


def search_tree(root):
    """Search a directory tree without following links or stopping on ACL errors."""
    root = os.path.abspath(root)
    found = wix_directory(root)
    if found:
        return found
    if not os.path.isdir(root):
        return None

    try:
        for current, directories, files in os.walk(root, topdown=True, followlinks=False):
            directories[:] = [directory for directory in directories
                               if directory not in {"$Recycle.Bin", "System Volume Information"}]
            names = set(files)
            if "wix.exe" in names or ("candle.exe" in names and "light.exe" in names):
                found = wix_directory(current)
                if found:
                    return found
    except (OSError, PermissionError):
        return None
    return None


def registry_install_locations():
    if winreg is None:
        return []

    locations = []
    uninstall_path = r"SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall"
    views = [0]
    if hasattr(winreg, "KEY_WOW64_64KEY"):
        views += [winreg.KEY_WOW64_64KEY, winreg.KEY_WOW64_32KEY]

    for hive in (winreg.HKEY_LOCAL_MACHINE, winreg.HKEY_CURRENT_USER):
        for view in views:
            try:
                with winreg.OpenKey(hive, uninstall_path, 0,
                                    winreg.KEY_READ | view) as root:
                    for index in range(winreg.QueryInfoKey(root)[0]):
                        try:
                            subkey_name = winreg.EnumKey(root, index)
                            with winreg.OpenKey(root, subkey_name) as subkey:
                                display_name = winreg.QueryValueEx(subkey, "DisplayName")[0]
                                install_location = winreg.QueryValueEx(subkey, "InstallLocation")[0]
                                if "wix" in str(display_name).lower() and install_location:
                                    locations.append(str(install_location))
                        except (OSError, TypeError):
                            continue
            except OSError:
                continue
    return locations


def cached_wix_directory(cache_file):
    if not cache_file or not os.path.isfile(cache_file):
        return None
    try:
        with open(cache_file, "r", encoding="utf-8") as stream:
            return wix_directory(stream.readline().strip())
    except (OSError, UnicodeError):
        return None


def remember_wix_directory(cache_file, directory):
    if not cache_file:
        return
    try:
        os.makedirs(os.path.dirname(os.path.abspath(cache_file)), exist_ok=True)
        with open(cache_file, "w", encoding="utf-8", newline="\n") as stream:
            stream.write(directory + "\n")
    except OSError as error:
        print("Could not write WiX cache: {}".format(error), file=sys.stderr)


def report_wix_directory(directory, cache_file):
    remember_wix_directory(cache_file, directory)
    print(directory)
    return 0


def main():
    parser = argparse.ArgumentParser(description="Find the WiX Toolset directory for jpackage.")
    parser.add_argument("--cache-file", help="File used to cache the discovered WiX directory.")
    arguments = parser.parse_args()

    if os.name != "nt":
        print("WiX discovery is only supported on Windows.", file=sys.stderr)
        return 1

    cached = cached_wix_directory(arguments.cache_file)
    if cached:
        print(cached)
        return 0

    candidates = []
    candidates.extend(os.environ.get("PATH", "").split(os.pathsep))
    candidates.extend(registry_install_locations())
    candidates.extend([
        os.environ.get("ProgramFiles"),
        os.environ.get("ProgramFiles(x86)"),
        os.environ.get("ProgramData"),
        os.environ.get("LOCALAPPDATA"),
        os.environ.get("APPDATA"),
        os.environ.get("USERPROFILE"),
        os.environ.get("ChocolateyInstall"),
        os.environ.get("ChocolateyToolsLocation"),
        os.path.join(os.environ.get("USERPROFILE", ""), "scoop"),
        os.path.join(os.environ.get("ProgramData", ""), "scoop"),
    ])

    seen = set()
    for candidate in candidates:
        if not candidate:
            continue
        candidate = os.path.abspath(os.path.expandvars(os.path.expanduser(candidate)))
        if candidate.lower() in seen:
            continue
        seen.add(candidate.lower())
        found = wix_directory(candidate) or search_tree(candidate)
        if found:
            return report_wix_directory(found, arguments.cache_file)

    print("WiX was not found in standard locations; scanning filesystem drives...",
          file=sys.stderr)
    for drive in string.ascii_uppercase:
        root = drive + ":\\"
        if os.path.isdir(root):
            found = search_tree(root)
            if found:
                return report_wix_directory(found, arguments.cache_file)

    print("Could not locate WiX. Searched PATH, registry entries, common install roots, and all filesystem drives.",
          file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())
