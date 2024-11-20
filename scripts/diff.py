#!/usr/bin/python
from itertools import zip_longest
import sys
import os

def diff(std, src):
    std_lines = map(str.strip, std.strip().splitlines())
    src_lines = map(str.strip, src.strip().splitlines())
    for idx, (std_line, src_line) in enumerate(zip_longest(std_lines, src_lines)):
        if std_line != src_line:
            return idx+1, std_line, src_line
    return 0, None, None

def do_diff(std_path, src_path):
    with open(std_path) as in_std:
        std_content = in_std.read()

    with open(src_path) as in_src:
        src_content = in_src.read()

    line_no, std_beginline, src_beginline = diff(std_content, src_content)

    if line_no == 0:
        print(f"{os.path.basename(src_path)}: The src file is the same as std file.")
    else:
        print(f"{os.path.basename(src_path)}: Different begin at line {line_no}:")
        print("std: " + (std_beginline or ""))
        print("src: " + (src_beginline or ""))
    print()  # 添加一个空行，使输出更易读

def compare_folders(src_folder, std_folder):
    for filename in os.listdir(src_folder):
        if filename.endswith('.txt'):
            src_path = os.path.join(src_folder, filename)
            std_path = os.path.join(std_folder, filename)
            
            if os.path.exists(std_path):
                do_diff(std_path, src_path)
            else:
                print(f"Warning: {filename} not found in std folder.")

if __name__ == '__main__':
    if len(sys.argv) == 3:
        src_folder, std_folder = sys.argv[1], sys.argv[2]
    else:
        src_folder, std_folder = './data/out', './data/std'
    
    compare_folders(src_folder, std_folder)
