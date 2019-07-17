import numpy as np
from numpy.linalg import norm


class Model:
    def __init__(self, *args):
        # A list of bi-phonemes frequencies, we'll name it models, a model for each corpus
        self._models = []
        self._models.extend(args)

        # Initialize S (self.separating_angles)
        # S will contain the angles separating the models
        self.angles_between_models = [[0 for _ in range(len(args))] for _ in range(len(args))]

        # Calculate the angles that separates the models,
        # and store each value in it's appropriate location in S
        for index1, model1 in enumerate(self._models):
            for index2, model2 in enumerate(self._models):
                # Calculate the scalar product
                # We can replace @ operator with np.dot()
                scalar_product = model1 @ model2
                # Calculate norm product
                norm_product = np.linalg.norm(model1) * np.linalg.norm(model2)
                # Calculate cos(alpha)
                # Calculate cos(alpha)
                # cos_alpha = (model1 @ model2) / (norm(model1) * norm(model2))
                cos_alpha = scalar_product / norm_product

                # cos_alpha = np.round(cos_alpha, 10)

                # if cos_alpha > 1:
                #     cos_alpha = 1
                # elif cos_alpha < -1:
                #     cos_alpha = -1
                angle = np.arccos(np.clip(cos_alpha, -1, 1))

                self.angles_between_models[index1][index2] = angle

        # Final step
        # Getting avg, max and std out of S
        # S is a symmetric matrix, so we need only the half -1 of its values
        # The upper or lower half doesn't matter
        # Converting S into a set
        x = set(np.ndarray.flatten(np.array(self.angles_between_models)))
        x.discard(0)
        x = np.array(list(x))
        self._max_s = np.max(x)
        self._avg_s = np.average(x)
        self._std_s = np.std(x)
        # Our beloved BETA
        self.beta = self._max_s + np.absolute(self._avg_s - self._std_s)

        # A global model for later usage when verifying a speech if healthy or not
        self.global_reference_model = np.average(np.array(self._models), axis=0)
