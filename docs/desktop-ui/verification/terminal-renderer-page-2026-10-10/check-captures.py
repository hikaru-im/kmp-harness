"""Independently inventory PNGs; visual and source review remain separate gates."""
from pathlib import Path
import argparse
import hashlib
import json
from PIL import Image


def inspect(directory):
    expected = {
        f'{width}x{height}-{theme}-{state}'
        for width, height in ((1440, 900), (600, 800))
        for theme in ('light', 'dark')
        for state in ('Running', 'Exited')
    } | {'1440x900-light-Unknown', '600x800-light-Unavailable'}
    present = {path.stem for path in directory.rglob('*.png')}
    assert expected <= present, 'Missing matrix captures: ' + str(sorted(expected - present))
    records = []
    for path in sorted(directory.rglob('*.png')):
        matrix_path = path.with_suffix('.matrix.json')
        geometry_path = path.with_suffix('.geometry.txt')
        assert matrix_path.is_file() and geometry_path.is_file(), 'Missing capture metadata: ' + str(path)
        matrix = json.loads(matrix_path.read_text())
        slug = f"{matrix['width']}x{matrix['height']}-{matrix['theme']}-{matrix['state']}"
        assert slug == path.stem, 'Capture identity mismatch: ' + str(path)
        assert matrix['controlled'] is (matrix['state'] in ('Unknown', 'Unavailable'))
        assert matrix['native_ime_verified'] is False and matrix['pixel_parity_verified'] is False
        geometry = geometry_path.read_text()
        assert 'coordinates=window-local' in geometry
        assert 'pty-terminal-panel=' in geometry
        if matrix['state'] != 'Unavailable':
            assert 'pty-terminal-grid=' in geometry and 'pty-terminal-input=' in geometry
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            assert image.format == 'PNG', str(path)
            assert image.size == (matrix['width'], matrix['height'])
            rgb = image.convert('RGB')
            extrema = rgb.getextrema()
            assert any(low != high for low, high in extrema), 'Blank image: ' + str(path)
            records.append(dict(path=str(path.relative_to(directory)),
                                width=image.width, height=image.height,
                                bytes=path.stat().st_size,
                                sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                                matrix=matrix,
                                rgb_extrema=extrema))
    assert len(records) >= 10, 'Need at least eight matrix PNGs and two uncertainty PNGs'
    for width, height in ((1440, 900), (600, 800)):
        assert sum(row['width'] == width and row['height'] == height for row in records) >= 4
    return dict(png_count=len(records), files=records, png_integrity_verified=True,
                required_matrix_metadata_verified=True,
                matrix_identity_and_geometry_pending_review=True,
                full_harness_app_source_pending_review=True, visual_review_pending=True,
                original_pixel_parity_verified=False, native_window_verified=False,
                native_ime_verified=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('directory', type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = inspect(args.directory)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + chr(10))
    print(json.dumps(dict(png_count=result['png_count'], output=str(args.output))))
