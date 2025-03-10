using System;
using System.Diagnostics;

class MatrixMultiplication
{
    static void OnMultLine(int m_ar, int m_br)
    {
        double[] pha = new double[m_ar * m_ar];
        double[] phb = new double[m_ar * m_ar];
        double[] phc = new double[m_ar * m_ar];

        // Inicializar matrizes
        for (int i = 0; i < m_ar; i++)
            for (int j = 0; j < m_ar; j++)
                pha[i * m_ar + j] = 1.0;

        for (int i = 0; i < m_br; i++)
            for (int j = 0; j < m_br; j++)
                phb[i * m_br + j] = i + 1.0;

        Stopwatch stopwatch = new Stopwatch();
        stopwatch.Start();

        // Multiplicação de matrizes
        for (int i = 0; i < m_ar; i++)
        {
            for (int k = 0; k < m_ar; k++)
            {
                double a = pha[i * m_ar + k];
                for (int j = 0; j < m_br; j++)
                {
                    phc[i * m_br + j] += a * phb[k * m_br + j];
                }
            }
        }

        stopwatch.Stop();
        Console.WriteLine($"Time: {stopwatch.Elapsed.TotalSeconds:F3} seconds");

        // Imprimir os primeiros 10 elementos da matriz de resultado
        Console.WriteLine("Result matrix:");
        for (int j = 0; j < Math.Min(10, m_br); j++)
        {
            Console.Write(phc[j] + " ");
        }
        Console.WriteLine();
    }

    static void Main(string[] args)
    {
        if (args.Length < 2)
        {
            Console.WriteLine("Usage: <operation> <size>");
            Console.WriteLine("Operation: 1=Multiplication, 2=Multiplication with line optimization");
            return;
        }

        int op = int.Parse(args[0]);
        int size = int.Parse(args[1]);

        switch (op)
        {
            case 1:
                OnMult(size);
                break;
            case 2:
                OnMultLine(size, size);
                break;
            default:
                Console.WriteLine("Invalid operation.");
                break;
        }
    }


    static void OnMult(int size)
    {
        double[,] A = new double[size, size];
        double[,] B = new double[size, size];
        double[,] C = new double[size, size];

        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
            {
                A[i, j] = 1.0;
                B[i, j] = i + 1;
            }

        Stopwatch stopwatch = new Stopwatch();
        stopwatch.Start();

        for (int i = 0; i < size; i++)
            for (int j = 0; j < size; j++)
            {
                double temp = 0;
                for (int k = 0; k < size; k++)
                {
                    temp += A[i, k] * B[k, j];
                }
                C[i, j] = temp;
            }

        stopwatch.Stop();
        Console.WriteLine($"Time: {stopwatch.Elapsed.TotalSeconds:F3} seconds");
        
        Console.WriteLine("Result matrix:");
        for (int j = 0; j < Math.Min(10, size); j++)
        {
            Console.Write(C[0, j] + " ");
        }
        Console.WriteLine();
    }
}
