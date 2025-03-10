.PHONY: all dotnet-build cpp-build set-permissions clean

# Compila tudo
all: dotnet-build cpp-build set-permissions

# Compila o projeto .NET
dotnet-build:
	dotnet build

# Compila o programa C++
cpp-build:
	g++ -O2 -o matrixproduct matrixproduct.cpp -lpapi -fopenmp

# Define permissões
set-permissions:
	chmod +x script.sh

# Limpa os binários
dotnet-clean:
	dotnet clean

cpp-clean:
	rm -f matrixproduct

clean: dotnet-clean cpp-clean
