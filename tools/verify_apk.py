"""Verify the packaged AdMob IDs and APK signature; print packaged permission declarations."""
import argparse
import pathlib
import re
import subprocess
import zipfile


def run(*args):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE, text=True).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=pathlib.Path)
    parser.add_argument("--tools", required=True, type=pathlib.Path, help="Android SDK build-tools directory")
    parser.add_argument("--app-id", default="ca-app-pub-3940256099942544~3347511713")
    parser.add_argument("--banner-id", default="ca-app-pub-3940256099942544/9214589741")
    parser.add_argument("--cert", required=True, help="Expected signer SHA-256 (colon separators optional)")
    args = parser.parse_args()
    win = (args.tools / "aapt.exe").exists()
    aapt = args.tools / ("aapt.exe" if win else "aapt")
    signer = args.tools / ("apksigner.bat" if win else "apksigner")
    signatures = run(signer, "verify", "--print-certs", args.apk)
    digests = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$", signatures, re.M)
    if [v.lower() for v in digests] != [args.cert.replace(":", "").strip().lower()]:
        raise SystemExit("Unexpected signer")
    manifest = run(aapt, "dump", "xmltree", args.apk, "AndroidManifest.xml")
    entries = manifest.split("E: meta-data")
    if not any("com.google.android.gms.ads.APPLICATION_ID" in e.split("E:")[0]
               and f'"{args.app_id}"' in e.split("E:")[0] for e in entries[1:]):
        raise SystemExit("Unexpected packaged AdMob application ID")
    with zipfile.ZipFile(args.apk) as apk:
        ids = {match.decode() for name in apk.namelist() if re.fullmatch(r"classes\d*\.dex", name)
               for match in re.findall(rb"ca-app-pub-\d{16}/\d{10}", apk.read(name))}
    if args.banner_id not in ids:
        raise SystemExit("Expected banner ID missing from packaged code")
    if args.banner_id.startswith("ca-app-pub-3940256099942544/") and any(
        not unit.startswith("ca-app-pub-3940256099942544/") for unit in ids
    ):
        raise SystemExit("Publisher banner ID present in a test APK")
    print(run(aapt, "dump", "permissions", args.apk))
    print(f"Verified {args.apk.name}: {args.app_id}, {args.banner_id}, signer {digests[0]}")


if __name__ == "__main__":
    main()
