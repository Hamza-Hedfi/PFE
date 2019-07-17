import numpy as np

from core.cleaner import Cleaner


class Corpus:
    def __init__(self, corpus=None, path_to_corpus=None):
        # Either we get a path to a file or the corpus it self
        # No checking if booth are None
        # May be added later, if further development
        self.corpus = corpus
        self.path_to_corpus = path_to_corpus

        # If path to corpus file is set
        # read the file and store its content to self.corpus
        if self.path_to_corpus:
            with open(file=self.path_to_corpus, encoding="utf-8") as file:
                self.corpus = file.read()

        # A dict of bi-phonemes and corresponding frequency
        self.bi_phonemes_frequencies = self.calc_bi_ph_freq()
        # A numpyArr of bi-phonemes frequencies
        self.bi_phonemes_frequencies_list = np.array(list(self.bi_phonemes_frequencies.values()))

    def calc_bi_ph_freq(self):
        # Step 1
        # Clean the corpus
        # Keep only arabic letters
        self.corpus = Cleaner.keep_only_arabic(self.corpus)
        # Remove diacritics, special characters, punctuations...
        self.corpus = Cleaner.remove_diacritics(self.corpus)
        self.corpus = Cleaner.remove_punctuations(self.corpus)
        # Normalize characters
        self.corpus = Cleaner.normalize_arabic(self.corpus)

        # Generate a dict with all possible bi-phonemes arrangement from the arabic alphabet
        # with values initialized to 0.0
        arabic_alphabet = 'ابجدهوزحطيكلمنسعفصقرشتثخذضظغ'
        bi_phonemes_arrangement = {''.join([letter_1, letter_2]): 0.0 for letter_1 in arabic_alphabet for letter_2 in
                                   arabic_alphabet}
        # Split the corpus into list of words
        corpus_word_list = self.corpus.split()

        bi_phonemes_from_corpus = []

        # for each word in the corpus
        for word in corpus_word_list:
            # exemple :
            # word = 'حمزة'
            # i in [1, 4]
            for i in range(1, len(word)):
                # in this example for each itr bi_phoneme_from_word will be
                # 'حم'
                # 'مز'
                # 'زة'
                bi_phoneme_from_word = ''.join([word[i - 1], word[i]])
                # Inc the corresponding key
                if bi_phoneme_from_word in bi_phonemes_arrangement:
                    bi_phonemes_arrangement[bi_phoneme_from_word] += 1
                bi_phonemes_from_corpus.append(bi_phoneme_from_word)
        # A dict of bi-phonemes and corresponding frequency
        bi_phonemes_frequencies = {bi_phoneme: (bi_phoneme_count / len(bi_phonemes_from_corpus)) for
                                   bi_phoneme, bi_phoneme_count in
                                   bi_phonemes_arrangement.items()}
        return bi_phonemes_frequencies
