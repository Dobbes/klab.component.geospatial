"""Independent GDAL/Rasterio oracle for captured GeotiffExportTest files.

Optional development dependency: rasterio>=1.4,<2. Pass the existing directory
that was supplied to Maven's -Dgeotiff.evidence.dir property.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def check(directory):
    try:
        import rasterio
    except ImportError:
        raise SystemExit("Install optional rasterio>=1.4,<2 for independent TIFF checks") from None
    cases = {
        "projected-float.tiff": (3857, (100, 0, 200000, 0, -100, 6000400), "float32", "degree_angle & <source>"),
        "projected-double.tiff": (3857, (100, 0, 200000, 0, -100, 6000400), "float64", None),
        "unicode-unit.tiff": (3857, (100, 0, 200000, 0, -100, 6000400), "float32", "µmol m⁻² s⁻¹ & <source>"),
        "geographic-latfirst.tiff": (4326, (.1, 0, 10, 0, -.1, 50), "float32", None),
        "geographic-lonfirst.tiff": (4326, (.1, 0, 10, 0, -.1, 50), "float32", None),
        "shifted-origin.tiff": (3857, (100, 0, 200000, 0, -100, 6000400), "float32", None),
        "rotated-affine.tiff": (3857, (100, 10, 200000, 20, -100, 6000400), "float32", None),
        "literal-entities.tiff": (3857, (100, 0, 200000, 0, -100, 6000400), "float32", "&amp; &#176; &#xB0;"),
    }
    report = {"rasterio": rasterio.__version__, "gdal": rasterio.__gdal_version__, "passed": False, "files": []}
    try:
        for name, (epsg, transform, dtype, unit) in cases.items():
            path = directory / name
            with rasterio.open(path) as dataset:
                require((dataset.width, dataset.height, dataset.count) == (5, 4, 1), name + ": wrong shape")
                require(dataset.crs is not None and dataset.crs.to_epsg() == epsg, name + ": wrong CRS")
                require(dataset.dtypes == (dtype,) and dataset.units == (unit,), name + ": wrong dtype/unit")
                require(dataset.nodata is not None and math.isnan(dataset.nodata), name + ": missing NaN nodata")
                require(all(math.isfinite(value) and abs(value - expected) < 1e-8
                    for value, expected in zip(tuple(dataset.transform)[:6], transform)), name + ": wrong transform")
                values, masks = dataset.read(1), dataset.read_masks(1)
                a, b, c, d, e, f = transform
                for row in range(4):
                    for column in range(5):
                        x, y = dataset.xy(row, column)
                        require(math.isfinite(x) and math.isfinite(y) and
                            abs(x - (c + (column + .5)*a + (row + .5)*b)) < 1e-8 and
                            abs(y - (f + (column + .5)*d + (row + .5)*e)) < 1e-8, name + ": wrong cell center")
                        if (column, row) == (2, 2):
                            require(math.isnan(float(values[row, column])) and masks[row, column] == 0,
                                name + ": missingness changed")
                        else:
                            expected = 16777217.125 if name == "projected-double.tiff" and (column, row) == (0, 0) else column*10 + 3-row
                            require(float(values[row, column]) == expected and masks[row, column] == 255,
                                name + ": value or validity changed")
                require(values[3, 0] == 0 and masks[3, 0] == 255, name + ": valid zero lost")
                report["files"].append({"name": name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                    "crs": epsg, "dtype": dtype, "transform": list(transform), "unit": unit, "verified_cells": 20})
            print(name, "verified", flush=True)
        report["passed"] = True
    finally:
        (directory / "independent-raster.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    if not args.directory.is_dir():
        parser.error("Provide the existing Maven evidence directory")
    check(args.directory)


if __name__ == "__main__":
    main()
