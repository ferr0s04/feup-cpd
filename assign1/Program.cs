using System;
using System.Diagnostics;
using System.Runtime.InteropServices;

class MatrixMultiplication
{
    // Importa as funções PAPI
    [DllImport("libpapi.so")]
    private static extern int PAPI_library_init(int version);
    
    [DllImport("libpapi.so")]
    private static extern int PAPI_create_eventset(ref int EventSet);
    
    [DllImport("libpapi.so")]
    private static extern int PAPI_add_event(int EventSet, int eventCode);
    
    [DllImport("libpapi.so")]
    private static extern int PAPI_start(int EventSet);
    
    [DllImport("libpapi.so")]
    private static extern int PAPI_stop(int EventSet, long[] values);
    
    private const int PAPI_VER_CURRENT = 0x00000207;
    private const int PAPI_L1_DCM = unchecked((int)0x80000002); // L1 Data Cache Misses
    private const int PAPI_L2_DCM = unchecked((int)0x8000002B); // L2 Data Cache Misses


    static void Main(string[] args)
    {
        if (args.Length < 2)
        {
            Console.WriteLine("Usage: program <matrix_size> <operation>");
            Console.WriteLine("Operations: 1 (Standard Multiplication), 2 (Row-wise), 3 (Block Multiplication)");
            return;
        }

        int n = int.Parse(args[0]); 
        int operation = int.Parse(args[1]);
        int blockSize = (args.Length == 3) ? int.Parse(args[2]) : 2;

        double[,] A = new double[n, n];
        double[,] B = new double[n, n];
        double[,] C = new double[n, n];

        InitializeMatrix(A, 1.0);
        InitializeMatrix(B, (i) => i + 1);

        Stopwatch stopwatch = new Stopwatch();

        // Inicializa PAPI
        PAPI_library_init(PAPI_VER_CURRENT);
        int eventSet = 0;
        PAPI_create_eventset(ref eventSet);
        PAPI_add_event(eventSet, PAPI_L1_DCM);
        PAPI_add_event(eventSet, PAPI_L2_DCM);

        long[] values = new long[2];

        PAPI_start(eventSet);
        stopwatch.Start();

        switch (operation)
        {
            case 1:
                MultiplyStandard(A, B, C, n);
                break;
            case 2:
                MultiplyRowWise(A, B, C, n);
                break;
            case 3:
                MultiplyBlock(A, B, C, n, blockSize);
                break;
            default:
                Console.WriteLine("Invalid operation!");
                return;
        }

        stopwatch.Stop();
        PAPI_stop(eventSet, values);

        Console.WriteLine($"Time elapsed: {stopwatch.ElapsedMilliseconds / 1000.0} seconds");
        Console.WriteLine($"L1 Data Cache Misses: {values[0]}");
        Console.WriteLine($"L2 Data Cache Misses: {values[1]}");

        PrintMatrix(C, n);
    }

    static void InitializeMatrix(double[,] matrix, double value)
    {
        int n = matrix.GetLength(0);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                matrix[i, j] = value;
    }

    static void InitializeMatrix(double[,] matrix, Func<int, double> generator)
    {
        int n = matrix.GetLength(0);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                matrix[i, j] = generator(i);
    }

    static void MultiplyStandard(double[,] A, double[,] B, double[,] C, int n)
    {
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
            {
                double sum = 0;
                for (int k = 0; k < n; k++)
                    sum += A[i, k] * B[k, j];
                C[i, j] = sum;
            }
    }

    static void MultiplyRowWise(double[,] A, double[,] B, double[,] C, int n)
    {
        for (int i = 0; i < n; i++)
            for (int k = 0; k < n; k++)
                for (int j = 0; j < n; j++)
                    C[i, j] += A[i, k] * B[k, j];
    }

    static void MultiplyBlock(double[,] A, double[,] B, double[,] C, int n, int blockSize)
    {
        for (int i = 0; i < n; i += blockSize)
            for (int j = 0; j < n; j += blockSize)
                for (int k = 0; k < n; k += blockSize)
                    for (int ii = i; ii < Math.Min(i + blockSize, n); ii++)
                        for (int jj = j; jj < Math.Min(j + blockSize, n); jj++)
                        {
                            double sum = 0;
                            for (int kk = k; kk < Math.Min(k + blockSize, n); kk++)
                                sum += A[ii, kk] * B[kk, jj];
                            C[ii, jj] += sum;
                        }
    }

    static void PrintMatrix(double[,] matrix, int n)
    {
        Console.WriteLine("Result matrix:");
        for (int i = 0; i < Math.Min(n, 10); i++)
        {
            for (int j = 0; j < Math.Min(n, 10); j++)
                Console.Write(matrix[i, j] + " ");
            Console.WriteLine();
        }
    }
}
