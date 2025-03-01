#!/bin/bash

if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <option>"
    echo "Options:"
    echo "  1 - Multiplication for sizes 600x600 to 3000x3000 (step 400)"
    echo "  2 - Line multiplication for sizes 600x600 to 3000x3000 (step 400)"
    echo "  3 - Line multiplication for sizes 4096x4096 to 10240x10240 (step 2048)"
    echo "  4 - Block multiplication for sizes 4096x4096 to 10240x10240 (step 2048) with block sizes 128, 256 and 512"
    echo "  5 - Parallel multiplication for sizes 600x600 to 3000x3000 (step 400)"
    echo "  6 - Parallel line multiplication for sizes 600x600 to 3000x3000 (step 400)"
    exit 1
fi

case $1 in
    1)
        for size in {600..3000..400}; do
            echo "Running ./matrixproduct 1 $size"
            #./matrixproduct 1 $size
            echo "Running dotnet run 1 $size"
            dotnet run 1 $size
            echo "---------------------------------------"
        done
        ;;
    2)
        for size in {600..3000..400}; do
            echo "Running ./matrixproduct 2 $size"
            #./matrixproduct 2 $size
            echo "Running dotnet run 2 $size"
            dotnet run 2 $size
            echo "---------------------------------------"
        done
        ;;
    3)
        for size in {4096..10240..2048}; do
            echo "Running ./matrixproduct 2 $size"
            ./matrixproduct 2 $size
            echo "--------------------------------------"
        done
        ;;
    4)
        for size in {4096..10240..2048}; do
            for block in 128 256 512; do
                echo "Running ./matrixproduct 3 $size $block"
                ./matrixproduct 3 $size $block
                echo "--------------------------------------"
            done
        done
        ;;

    5)
        for size in {600..3000..400}; do
            echo "Running ./matrixproduct 4 $size"
            ./matrixproduct 4 $size
            #echo "Running dotnet run 4 $size"
            #dotnet run 4 $size
            echo "--------------------------------------"
        done
        ;;

    6)
        for size in {600..3000..400}; do
            echo "Running ./matrixproduct 5 $size"
            ./matrixproduct 5 $size
            #echo "Running dotnet run 5 $size"
            #dotnet run 5 $size
            echo "--------------------------------------"
        done
        ;;

    *)
        echo "Invalid option. Use 1-6."
        exit 1
        ;;
esac