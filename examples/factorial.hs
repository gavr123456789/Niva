-- ghc -dynamic  ./examples/factorial.hs
factorial :: Int -> Int
factorial 0 = 1
factorial n = n * factorial (n - 1)

main = printnln $ factorial 5



