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

output_file="results.csv"
echo "Option,Size,Block Size,Time,L1 DCM,L2 DCM" > $output_file

case $1 in
    1)
        for size in {600..3000..400}; do
            echo "Running dotnet run 1 $size"
            result=$(dotnet run 1 $size)
            #result=$(./matrixproduct 1 $size)            
            time=$(echo "$result" | grep "Time:" | awk '{print $2}')
            l1_dcm=$(echo "$result" | grep "L1 DCM:" | awk '{print $3}')
            l2_dcm=$(echo "$result" | grep "L2 DCM:" | awk '{print $3}')
            echo "1,$size,,${time},${l1_dcm},${l2_dcm}" >> $output_file
            echo "---------------------------------------"
        done
        ;;
    2)
        for size in {600..3000..400}; do
            echo "Running dotnet run 2 $size"
            result=$(dotnet run 2 $size)
            #result=$(./matrixproduct 2 $size)
            time=$(echo "$result" | grep "Time:" | awk '{print $2}')
            l1_dcm=$(echo "$result" | grep "L1 DCM:" | awk '{print $3}')
            l2_dcm=$(echo "$result" | grep "L2 DCM:" | awk '{print $3}')
            echo "2,$size,,${time},${l1_dcm},${l2_dcm}" >> $output_file
            echo "---------------------------------------"
        done
        ;;
    3)
        for size in {4096..10240..2048}; do
            echo "Running ./matrixproduct 2 $size"
            result=$(./matrixproduct 2 $size)
            time=$(echo "$result" | grep "Time:" | awk '{print $2}')
            l1_dcm=$(echo "$result" | grep "L1 DCM:" | awk '{print $3}')
            l2_dcm=$(echo "$result" | grep "L2 DCM:" | awk '{print $3}')
            echo "3,$size,,${time},${l1_dcm},${l2_dcm}" >> $output_file
            echo "--------------------------------------"
        done
        ;;
    4)
        for size in {4096..10240..2048}; do
            for block in 128 256 512; do
                echo "Running ./matrixproduct 3 $size $block"
                result=$(./matrixproduct 3 $size $block)
                time=$(echo "$result" | grep "Time:" | awk '{print $2}')
                l1_dcm=$(echo "$result" | grep "L1 DCM:" | awk '{print $3}')
                l2_dcm=$(echo "$result" | grep "L2 DCM:" | awk '{print $3}')
                echo "4,$size,$block,${time},${l1_dcm},${l2_dcm}" >> $output_file
                echo "--------------------------------------"
            done
        done
        ;;
    5)
        for size in {600..3000..400}; do
            echo "Running ./matrixproduct 4 $size"
            result=$(./matrixproduct 4 $size)
            time=$(echo "$result" | grep "Time:" | awk '{print $2}')
            l1_dcm=$(echo "$result" | grep "L1 DCM:" | awk '{print $3}')
            l2_dcm=$(echo "$result" | grep "L2 DCM:" | awk '{print $3}')
            echo "5,$size,,${time},${l1_dcm},${l2_dcm}" >> $output_file
            echo "--------------------------------------"
        done
        ;;
    6)
        for size in {600..3000..400}; do
            echo "Running ./matrixproduct 5 $size"
            result=$(./matrixproduct 5 $size)
            time=$(echo "$result" | grep "Time:" | awk '{print $2}')
            l1_dcm=$(echo "$result" | grep "L1 DCM:" | awk '{print $3}')
            l2_dcm=$(echo "$result" | grep "L2 DCM:" | awk '{print $3}')
            echo "6,$size,,${time},${l1_dcm},${l2_dcm}" >> $output_file
            echo "--------------------------------------"
        done
        ;;
    *)
        echo "Invalid option. Use 1-6."
        exit 1
        ;;
esac