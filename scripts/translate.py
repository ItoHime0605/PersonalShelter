import json
import csv
import os
from pathlib import Path

LANG_DIR = Path(__file__).parent.parent / "common" / "src" / "main" / "resources" / "assets" / "personaldimension" / "lang"
CSV_FILE = Path(__file__).parent / "translations.csv"

LANG_MAP = {
    "en_us": "English",
    "zh_cn": "Chinese (Simplified)",
    "zh_tw": "Chinese (Traditional)",
    "ja_jp": "Japanese",
    "ko_kr": "Korean",
    "fr_fr": "French",
    "de_de": "German",
    "es_es": "Spanish",
    "ru_ru": "Russian",
    "it_it": "Italian",
    "pt_br": "Portuguese (Brazil)",
    "nl_nl": "Dutch",
    "pl_pl": "Polish",
    "ar_sa": "Arabic",
    "vi_vn": "Vietnamese"
}


def load_all_langs():
    all_data = {}
    for lang_code in LANG_MAP.keys():
        lang_file = LANG_DIR / f"{lang_code}.json"
        if lang_file.exists():
            with open(lang_file, 'r', encoding='utf-8') as f:
                all_data[lang_code] = json.load(f)
        else:
            all_data[lang_code] = {}
    return all_data


def export_to_csv():
    all_data = load_all_langs()
    all_keys = set()
    for lang_data in all_data.values():
        all_keys.update(lang_data.keys())
    all_keys = sorted(all_keys)

    with open(CSV_FILE, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        header = ["key"] + list(LANG_MAP.keys())
        writer.writerow(header)
        for key in all_keys:
            row = [key]
            for lang_code in LANG_MAP.keys():
                row.append(all_data[lang_code].get(key, ""))
            writer.writerow(row)
    print(f"Exported {len(all_keys)} keys to {CSV_FILE}")


def import_from_csv():
    with open(CSV_FILE, 'r', encoding='utf-8') as f:
        reader = csv.reader(f)
        header = next(reader)
        lang_codes = header[1:]

        lang_data = {code: {} for code in lang_codes}
        for row in reader:
            key = row[0]
            if not key:
                continue
            for i, code in enumerate(lang_codes):
                value = row[i + 1]
                if value:
                    lang_data[code][key] = value

    for code in lang_codes:
        lang_file = LANG_DIR / f"{code}.json"
        with open(lang_file, 'w', encoding='utf-8') as f:
            json.dump(lang_data[code], f, ensure_ascii=False, indent=2, sort_keys=True)
        print(f"Generated {lang_file.name}: {len(lang_data[code])} keys")


def validate_keys():
    all_data = load_all_langs()
    base_keys = set(all_data["en_us"].keys())
    for code, data in all_data.items():
        data_keys = set(data.keys())
        missing = base_keys - data_keys
        extra = data_keys - base_keys
        if missing:
            print(f"[WARN] {code} missing {len(missing)} keys")
        if extra:
            print(f"[WARN] {code} has {len(extra)} extra keys")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Minecraft mod translation manager")
    parser.add_argument("action", choices=["export", "import", "validate"], help="Action to perform")
    args = parser.parse_args()

    if args.action == "export":
        export_to_csv()
    elif args.action == "import":
        import_from_csv()
    elif args.action == "validate":
        validate_keys()
