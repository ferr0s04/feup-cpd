using System;
using System.Diagnostics;

class MatrixMultiplication
{
    static void OnMult(int size)
    {
        double[,] A = new double[size, size];
        double[,] B = new double[size, size];
        double[,] C = new double[size, size];

        // Initialize matrices
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                A[i, j] = 1.0;
                B[i, j] = i + 1;
            }
        }

        Stopwatch stopwatch = new Stopwatch();
        stopwatch.Start();

        // Matrix multiplication
        for (int i = 0; i < size; i++)
        {
            for (int j = 0; j < size; j++)
            {
                double temp = 0;
                for (int k = 0; k < size; k++)
                {
                    temp += A[i, k] * B[k, j];
                }
                C[i, j] = temp;
            }
        }

        stopwatch.Stop();
        Console.WriteLine($"Time: {stopwatch.Elapsed.TotalSeconds:F3} seconds");

        // Print first 10 elements of the result matrix
        Console.WriteLine("Result matrix:");
        for (int j = 0; j < Math.Min(10, size); j++)
        {
            Console.Write(C[0, j] + " ");
        }
        Console.WriteLine();
    }

    static void Main(string[] args)
    {
        if (args.Length < 2)
        {
            Console.WriteLine("Usage: <operation> <size>");
            Console.WriteLine("Operation: 1=Multiplication");
            return;
        }

        int op = int.Parse(args[0]);
        int size = int.Parse(args[1]);

        switch (op)
        {
            case 1:
                OnMult(size);
                break;
            default:
                Console.WriteLine("Invalid operation.");
                break;
        }
    }
}
